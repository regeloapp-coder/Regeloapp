-- Regélő app v1.2 – új fejezet / új történet értesítések, aktivitásfolyam, ajánlások, olvasási kihívás,
-- jelvények, olvasókörök és írói kihívások. Idempotens: többször is futtatható.

-- =====================================================================================
-- 0) Új értesítés- és könyvtárelem-típusok
-- =====================================================================================
alter table public.community_notifications drop constraint if exists community_notifications_kind_check;
alter table public.community_notifications add constraint community_notifications_kind_check check (kind = any (array[
  'FOLLOW','LIKE','COMMENT','REPLY','MESSAGE','ANNOUNCEMENT','RATING',
  'NEW_CHAPTER','NEW_BOOK','CIRCLE_POST','CHALLENGE'
]));

-- NOTE: olvasói jegyzet/kiemelés, FINISHED: végigolvasott könyv, GOAL: éves olvasási cél.
alter table public.reader_library_items drop constraint if exists reader_library_items_kind_check;
alter table public.reader_library_items add constraint reader_library_items_kind_check check (kind = any (array[
  'FAVORITE','COLLECTION','NOTE','FINISHED','GOAL'
]));

-- =====================================================================================
-- 1) Megjelenési időpontok (az első publikálás ideje; a későbbi szerkesztés nem változtatja)
-- =====================================================================================
alter table public.books add column if not exists published_at timestamptz;
alter table public.chapters add column if not exists published_at timestamptz;

-- A megjelenési idő beállítása önmagában nem „frissíti” a könyvet.
create or replace function public.regelo_books_touch()
returns trigger language plpgsql set search_path = public as $$
begin
  if (to_jsonb(new) - 'updated_at' - 'view_count' - 'published_at') is distinct from
     (to_jsonb(old) - 'updated_at' - 'view_count' - 'published_at') then
    new.updated_at := now();
  end if;
  return new;
end;
$$;

create or replace function public.regelo_chapters_touch_book()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'UPDATE' and (to_jsonb(new) - 'published_at') is not distinct from (to_jsonb(old) - 'published_at') then
    return null;
  end if;
  update public.books
     set updated_at = now()
   where id = case when tg_op = 'DELETE' then old.book_id else new.book_id end;
  return null;
end;
$$;

-- Visszamenőleges kitöltés (a „frissítve” időpontok érintése nélkül).
alter table public.chapters disable trigger regelo_chapters_touch_book;
update public.books set published_at = coalesce(created_at, updated_at, now())
 where published_at is null and publish_status = 'PUBLISHED';
update public.chapters c set published_at = coalesce(c.updated_at, now())
 where c.published_at is null and c.is_draft = false
   and exists (select 1 from public.books b where b.id = c.book_id and b.publish_status = 'PUBLISHED');
alter table public.chapters enable trigger regelo_chapters_touch_book;

create index if not exists chapters_published_idx on public.chapters (published_at desc) where published_at is not null;
create index if not exists books_published_idx on public.books (published_at desc) where published_at is not null;

-- Fejezet: amikor először válik olvashatóvá (nem piszkozat, a könyv publikus), megkapja a megjelenési időt.
create or replace function public.regelo_chapter_published_at()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.is_draft = false and new.published_at is null
     and exists (select 1 from public.books b where b.id = new.book_id and b.publish_status = 'PUBLISHED') then
    new.published_at := now();
  end if;
  return new;
end;
$$;
drop trigger if exists regelo_chapter_published_at on public.chapters;
create trigger regelo_chapter_published_at before insert or update on public.chapters
  for each row execute function public.regelo_chapter_published_at();

-- Könyv: az első publikáláskor kap megjelenési időt.
create or replace function public.regelo_book_published_at()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.publish_status = 'PUBLISHED' and new.published_at is null then
    new.published_at := now();
  end if;
  return new;
end;
$$;
drop trigger if exists regelo_book_published_at on public.books;
create trigger regelo_book_published_at before insert or update on public.books
  for each row execute function public.regelo_book_published_at();

-- =====================================================================================
-- 2) Értesítés új fejezetről (követőknek, akik elmentették, és akik olvassák)
-- =====================================================================================
create or replace function public.regelo_notify_new_chapter()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_book public.books%rowtype;
begin
  if new.published_at is null then return null; end if;
  if tg_op = 'UPDATE' and old.published_at is not null then return null; end if;
  -- A könyv első megjelenésekor a fejezetekről nem megy külön értesítés (az „új történet” szól róla).
  if coalesce(current_setting('regelo.bulk_publish', true), '') = '1' then return null; end if;
  select * into v_book from public.books where id = new.book_id;
  if v_book.id is null or v_book.publish_status <> 'PUBLISHED' then return null; end if;
  if v_book.published_at is not null and v_book.published_at > now() - interval '10 minutes' then return null; end if;

  with recipients as (
    select f.follower_id as uid from public.user_follows f where f.followed_author_id = v_book.author_id
    union select s.user_id from public.reader_saved_books s where s.book_id = v_book.id and s.is_saved
    union select p.user_id from public.reader_progress p where p.book_id = v_book.id and p.is_deleted = false
  ),
  merged as (
    -- Ha az olvasónak már van friss, olvasatlan értesítése erről a történetről, azt frissítjük (nem küldünk újabb pusht).
    update public.community_notifications n
       set payload = n.payload || jsonb_build_object(
             'chapter_id', new.id, 'chapter_title', new.title,
             'count', coalesce((n.payload ->> 'count')::int, 1) + 1)
     where n.kind = 'NEW_CHAPTER' and n.book_id = v_book.id and n.read_at is null
       and n.created_at > now() - interval '12 hours'
       and n.recipient_id in (select uid from recipients)
    returning n.recipient_id
  )
  insert into public.community_notifications (recipient_id, actor_id, kind, book_id, payload)
  select r.uid, v_book.author_id, 'NEW_CHAPTER', v_book.id,
         jsonb_build_object('chapter_id', new.id, 'chapter_title', new.title, 'count', 1)
    from recipients r
   where r.uid is not null and r.uid <> v_book.author_id
     and r.uid not in (select recipient_id from merged);
  return null;
end;
$$;
drop trigger if exists regelo_notify_new_chapter on public.chapters;
-- (Nem „update of published_at”: a BEFORE trigger által beállított oszlopot az nem venné észre.)
create trigger regelo_notify_new_chapter after insert or update on public.chapters
  for each row execute function public.regelo_notify_new_chapter();

-- =====================================================================================
-- 3) Értesítés új történetről (követőknek) – csak az első megjelenéskor
-- =====================================================================================
create or replace function public.regelo_notify_new_book()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.publish_status <> 'PUBLISHED' then return null; end if;
  if tg_op = 'UPDATE' and old.publish_status = 'PUBLISHED' then return null; end if;

  -- A már nem piszkozat fejezetek is most jelennek meg (egyenkénti értesítés nélkül).
  perform set_config('regelo.bulk_publish', '1', true);
  update public.chapters set published_at = now()
   where book_id = new.id and is_draft = false and published_at is null;
  perform set_config('regelo.bulk_publish', '', true);

  if tg_op = 'UPDATE' and old.published_at is not null then return null; end if; -- újrapublikálás
  insert into public.community_notifications (recipient_id, actor_id, kind, book_id, payload)
  select f.follower_id, new.author_id, 'NEW_BOOK', new.id, '{}'::jsonb
    from public.user_follows f
   where f.followed_author_id = new.author_id and f.follower_id <> new.author_id;
  return null;
end;
$$;
drop trigger if exists regelo_notify_new_book on public.books;
create trigger regelo_notify_new_book after insert or update on public.books
  for each row execute function public.regelo_notify_new_book();

-- =====================================================================================
-- 4) Olvasókörök
-- =====================================================================================
create table if not exists public.reading_circles (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  description text not null default '',
  book_id uuid references public.books(id) on delete set null,
  member_count int not null default 0,
  post_count int not null default 0,
  last_post_at timestamptz,
  created_at timestamptz not null default now(),
  constraint reading_circles_name_check check (char_length(trim(name)) between 3 and 60),
  constraint reading_circles_description_check check (char_length(description) <= 1000)
);

create table if not exists public.reading_circle_members (
  circle_id uuid not null references public.reading_circles(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'MEMBER',
  muted boolean not null default false,
  joined_at timestamptz not null default now(),
  primary key (circle_id, user_id),
  constraint reading_circle_members_role_check check (role in ('OWNER','MEMBER'))
);
create index if not exists reading_circle_members_user_idx on public.reading_circle_members (user_id);

create table if not exists public.reading_circle_posts (
  id uuid primary key default gen_random_uuid(),
  circle_id uuid not null references public.reading_circles(id) on delete cascade,
  author_id uuid not null references auth.users(id) on delete cascade,
  parent_id uuid references public.reading_circle_posts(id) on delete cascade,
  body text not null,
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  constraint reading_circle_posts_body_check check (char_length(trim(body)) between 1 and 4000)
);
create index if not exists reading_circle_posts_circle_idx on public.reading_circle_posts (circle_id, created_at desc);

alter table public.reading_circles enable row level security;
alter table public.reading_circle_members enable row level security;
alter table public.reading_circle_posts enable row level security;

create or replace function public.regelo_is_circle_member(p_circle uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and exists (
    select 1 from public.reading_circle_members m where m.circle_id = p_circle and m.user_id = auth.uid());
$$;

create or replace function public.regelo_is_circle_owner(p_circle uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and exists (
    select 1 from public.reading_circles c where c.id = p_circle and c.owner_id = auth.uid());
$$;

drop policy if exists reading_circles_read on public.reading_circles;
create policy reading_circles_read on public.reading_circles for select to authenticated using (true);
drop policy if exists reading_circles_insert on public.reading_circles;
create policy reading_circles_insert on public.reading_circles for insert to authenticated with check (owner_id = auth.uid());
drop policy if exists reading_circles_update on public.reading_circles;
create policy reading_circles_update on public.reading_circles for update to authenticated
  using (owner_id = auth.uid() or public.regelo_is_moderator())
  with check (owner_id = auth.uid() or public.regelo_is_moderator());
drop policy if exists reading_circles_delete on public.reading_circles;
create policy reading_circles_delete on public.reading_circles for delete to authenticated
  using (owner_id = auth.uid() or public.regelo_is_moderator());

drop policy if exists reading_circle_members_read on public.reading_circle_members;
create policy reading_circle_members_read on public.reading_circle_members for select to authenticated using (true);
-- Csatlakozni bárki tud, kivéve, ha a kör tulajdonosa letiltotta.
create or replace function public.regelo_can_join_circle(p_circle uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and exists (select 1 from public.reading_circles c where c.id = p_circle)
     and not exists (select 1 from public.reading_circles c join public.blocked_users b
                      on b.user_id = c.owner_id and b.blocked_id = auth.uid() where c.id = p_circle);
$$;
drop policy if exists reading_circle_members_join on public.reading_circle_members;
create policy reading_circle_members_join on public.reading_circle_members for insert to authenticated
  with check (user_id = auth.uid() and role = 'MEMBER' and public.regelo_can_join_circle(circle_id));
drop policy if exists reading_circle_members_update on public.reading_circle_members;
create policy reading_circle_members_update on public.reading_circle_members for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists reading_circle_members_leave on public.reading_circle_members;
create policy reading_circle_members_leave on public.reading_circle_members for delete to authenticated
  using ((user_id = auth.uid() and role <> 'OWNER')
         or (role <> 'OWNER' and (public.regelo_is_circle_owner(circle_id) or public.regelo_is_moderator())));

drop policy if exists reading_circle_posts_read on public.reading_circle_posts;
create policy reading_circle_posts_read on public.reading_circle_posts for select to authenticated
  using ((deleted_at is null or author_id = auth.uid() or public.regelo_is_circle_owner(circle_id) or public.regelo_is_moderator())
         and not public.regelo_i_blocked(author_id));
drop policy if exists reading_circle_posts_insert on public.reading_circle_posts;
create policy reading_circle_posts_insert on public.reading_circle_posts for insert to authenticated
  with check (author_id = auth.uid() and deleted_at is null and public.regelo_is_circle_member(circle_id));
drop policy if exists reading_circle_posts_update on public.reading_circle_posts;
create policy reading_circle_posts_update on public.reading_circle_posts for update to authenticated
  using (author_id = auth.uid() or public.regelo_is_circle_owner(circle_id) or public.regelo_is_moderator())
  with check (author_id = auth.uid() or public.regelo_is_circle_owner(circle_id) or public.regelo_is_moderator());

-- A létrehozó automatikusan tulajdonos-tag; a tagszám és a bejegyzésszám karbantartása.
create or replace function public.regelo_circle_created()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.reading_circle_members (circle_id, user_id, role) values (new.id, new.owner_id, 'OWNER')
  on conflict (circle_id, user_id) do update set role = 'OWNER';
  return null;
end;
$$;
drop trigger if exists regelo_circle_created on public.reading_circles;
create trigger regelo_circle_created after insert on public.reading_circles
  for each row execute function public.regelo_circle_created();

create or replace function public.regelo_circle_member_count()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_circle uuid := coalesce(new.circle_id, old.circle_id);
begin
  update public.reading_circles
     set member_count = (select count(*) from public.reading_circle_members m where m.circle_id = v_circle)
   where id = v_circle;
  return null;
end;
$$;
drop trigger if exists regelo_circle_member_count on public.reading_circle_members;
create trigger regelo_circle_member_count after insert or delete on public.reading_circle_members
  for each row execute function public.regelo_circle_member_count();

create or replace function public.regelo_circle_post_changed()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_circle public.reading_circles%rowtype;
  v_parent_author uuid;
begin
  select * into v_circle from public.reading_circles where id = new.circle_id;
  update public.reading_circles
     set post_count = (select count(*) from public.reading_circle_posts p where p.circle_id = new.circle_id and p.deleted_at is null),
         last_post_at = (select max(created_at) from public.reading_circle_posts p where p.circle_id = new.circle_id and p.deleted_at is null)
   where id = new.circle_id;
  if tg_op <> 'INSERT' then return null; end if;

  if new.parent_id is null then
    -- Új téma: a kör (nem némított) tagjai kapnak értesítést.
    insert into public.community_notifications (recipient_id, actor_id, kind, payload)
    select m.user_id, new.author_id, 'CIRCLE_POST',
           jsonb_build_object('circle_id', new.circle_id, 'circle_name', v_circle.name, 'post_id', new.id,
                              'text', left(new.body, 300))
      from public.reading_circle_members m
     where m.circle_id = new.circle_id and m.user_id <> new.author_id and m.muted = false;
  else
    -- Válasz: a téma szerzője kap értesítést.
    select author_id into v_parent_author from public.reading_circle_posts where id = new.parent_id;
    if v_parent_author is not null and v_parent_author <> new.author_id then
      insert into public.community_notifications (recipient_id, actor_id, kind, payload)
      values (v_parent_author, new.author_id, 'CIRCLE_POST',
              jsonb_build_object('circle_id', new.circle_id, 'circle_name', v_circle.name, 'post_id', new.parent_id,
                                 'reply', true, 'text', left(new.body, 300)));
    end if;
  end if;
  return null;
end;
$$;
drop trigger if exists regelo_circle_post_changed on public.reading_circle_posts;
create trigger regelo_circle_post_changed after insert or update of deleted_at on public.reading_circle_posts
  for each row execute function public.regelo_circle_post_changed();

-- Védelmek: csak egy szintű válasz ugyanabban a körben; más bejegyzésének szövegét senki nem írhatja át
-- (a kör tulajdonosa és a moderátor csak elrejtheti).
create or replace function public.regelo_circle_post_guard()
returns trigger language plpgsql set search_path = public as $$
begin
  if tg_op = 'INSERT' then
    if new.parent_id is not null and not exists (
      select 1 from public.reading_circle_posts p
       where p.id = new.parent_id and p.circle_id = new.circle_id and p.parent_id is null) then
      raise exception 'A válasz csak ugyanennek a körnek egy témájára vonatkozhat.' using errcode = '22023';
    end if;
  elsif new.body <> old.body and auth.uid() is distinct from old.author_id then
    raise exception 'Csak a saját bejegyzésed szövegét szerkesztheted.' using errcode = '42501';
  end if;
  return new;
end;
$$;
drop trigger if exists regelo_circle_post_guard on public.reading_circle_posts;
create trigger regelo_circle_post_guard before insert or update on public.reading_circle_posts
  for each row execute function public.regelo_circle_post_guard();

-- Oszlopszintű jogok: a számlálókat és a tulajdonost csak az adatbázis állítja.
revoke update on public.reading_circles from authenticated;
grant update (name, description, book_id) on public.reading_circles to authenticated;
revoke update on public.reading_circle_members from authenticated;
grant update (muted) on public.reading_circle_members to authenticated;
revoke update on public.reading_circle_posts from authenticated;
grant update (body, deleted_at) on public.reading_circle_posts to authenticated;

-- =====================================================================================
-- 5) Írói kihívások (pályázatok)
-- =====================================================================================
create table if not exists public.writing_challenges (
  id uuid primary key default gen_random_uuid(),
  created_by uuid not null references auth.users(id) on delete cascade,
  title text not null,
  description text not null default '',
  theme_tag text not null default '',
  min_words int not null default 0,
  max_words int not null default 0,
  starts_at timestamptz not null default now(),
  submission_deadline timestamptz not null,
  voting_deadline timestamptz not null,
  created_at timestamptz not null default now(),
  constraint writing_challenges_title_check check (char_length(trim(title)) between 3 and 100),
  constraint writing_challenges_description_check check (char_length(description) <= 4000),
  constraint writing_challenges_words_check check (min_words >= 0 and max_words >= 0 and (max_words = 0 or max_words >= min_words)),
  constraint writing_challenges_dates_check check (submission_deadline > starts_at and voting_deadline > submission_deadline)
);

create table if not exists public.writing_challenge_entries (
  challenge_id uuid not null references public.writing_challenges(id) on delete cascade,
  book_id uuid not null references public.books(id) on delete cascade,
  author_id uuid not null references auth.users(id) on delete cascade,
  note text not null default '',
  created_at timestamptz not null default now(),
  primary key (challenge_id, book_id),
  constraint writing_challenge_entries_one_per_author unique (challenge_id, author_id),
  constraint writing_challenge_entries_note_check check (char_length(note) <= 500)
);

create table if not exists public.writing_challenge_votes (
  challenge_id uuid not null,
  book_id uuid not null,
  voter_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (challenge_id, voter_id),
  foreign key (challenge_id, book_id) references public.writing_challenge_entries(challenge_id, book_id) on delete cascade
);

alter table public.writing_challenges enable row level security;
alter table public.writing_challenge_entries enable row level security;
alter table public.writing_challenge_votes enable row level security;

drop policy if exists writing_challenges_read on public.writing_challenges;
create policy writing_challenges_read on public.writing_challenges for select to authenticated using (true);
drop policy if exists writing_challenges_manage on public.writing_challenges;
drop policy if exists writing_challenges_insert on public.writing_challenges;
create policy writing_challenges_insert on public.writing_challenges for insert to authenticated
  with check (public.regelo_is_moderator() and created_by = auth.uid());
drop policy if exists writing_challenges_update on public.writing_challenges;
create policy writing_challenges_update on public.writing_challenges for update to authenticated
  using (public.regelo_is_moderator()) with check (public.regelo_is_moderator());
drop policy if exists writing_challenges_delete on public.writing_challenges;
create policy writing_challenges_delete on public.writing_challenges for delete to authenticated
  using (public.regelo_is_moderator());

drop policy if exists writing_challenge_entries_read on public.writing_challenge_entries;
create policy writing_challenge_entries_read on public.writing_challenge_entries for select to authenticated using (true);
drop policy if exists writing_challenge_entries_insert on public.writing_challenge_entries;
create policy writing_challenge_entries_insert on public.writing_challenge_entries for insert to authenticated
  with check (
    author_id = auth.uid()
    and exists (select 1 from public.books b where b.id = book_id and b.author_id = auth.uid() and b.publish_status = 'PUBLISHED')
    and exists (select 1 from public.writing_challenges c where c.id = challenge_id
                and now() >= c.starts_at and now() < c.submission_deadline
                and (c.min_words = 0 or (select coalesce(sum(ch.word_count), 0) from public.chapters ch
                      where ch.book_id = writing_challenge_entries.book_id and ch.is_draft = false) >= c.min_words)
                and (c.max_words = 0 or (select coalesce(sum(ch.word_count), 0) from public.chapters ch
                      where ch.book_id = writing_challenge_entries.book_id and ch.is_draft = false) <= c.max_words))
  );
drop policy if exists writing_challenge_entries_delete on public.writing_challenge_entries;
create policy writing_challenge_entries_delete on public.writing_challenge_entries for delete to authenticated
  using (
    public.regelo_is_moderator()
    or (author_id = auth.uid() and exists (select 1 from public.writing_challenges c
                                           where c.id = challenge_id and now() < c.submission_deadline))
  );

-- A szavazatok titkosak: mindenki csak a sajátját látja; az eredmény a szavazás lezárulta után nyilvános.
drop policy if exists writing_challenge_votes_read on public.writing_challenge_votes;
create policy writing_challenge_votes_read on public.writing_challenge_votes for select to authenticated using (voter_id = auth.uid());
drop policy if exists writing_challenge_votes_insert on public.writing_challenge_votes;
create policy writing_challenge_votes_insert on public.writing_challenge_votes for insert to authenticated
  with check (
    voter_id = auth.uid()
    and exists (select 1 from public.writing_challenges c where c.id = challenge_id
                and now() >= c.submission_deadline and now() < c.voting_deadline)
    and not exists (select 1 from public.writing_challenge_entries e
                    where e.challenge_id = writing_challenge_votes.challenge_id
                      and e.book_id = writing_challenge_votes.book_id and e.author_id = auth.uid())
  );
drop policy if exists writing_challenge_votes_delete on public.writing_challenge_votes;
create policy writing_challenge_votes_delete on public.writing_challenge_votes for delete to authenticated
  using (voter_id = auth.uid() and exists (select 1 from public.writing_challenges c
                                           where c.id = challenge_id and now() < c.voting_deadline));

-- Eredmények: szavazatszám nevezésenként, csak a szavazás lezárulta után (moderátor bármikor látja).
create or replace function public.regelo_challenge_results(p_challenge uuid)
returns table (book_id uuid, author_id uuid, votes int, rank int)
language sql stable security definer set search_path = public as $$
  with c as (select * from public.writing_challenges where id = p_challenge),
  counted as (
    select e.book_id, e.author_id, (select count(*)::int from public.writing_challenge_votes v
                                     where v.challenge_id = e.challenge_id and v.book_id = e.book_id) as votes
      from public.writing_challenge_entries e where e.challenge_id = p_challenge
  )
  select k.book_id, k.author_id, k.votes, (rank() over (order by k.votes desc))::int
    from counted k, c
   where now() >= c.voting_deadline or public.regelo_is_moderator()
   order by k.votes desc, k.book_id;
$$;

-- Győztesek (lezárt kihívások első helyezettjei; holtversenyben mind).
create or replace function public.regelo_challenge_winners()
returns table (challenge_id uuid, book_id uuid, author_id uuid, votes int)
language sql stable security definer set search_path = public as $$
  with counted as (
    select e.challenge_id, e.book_id, e.author_id,
           (select count(*)::int from public.writing_challenge_votes v
             where v.challenge_id = e.challenge_id and v.book_id = e.book_id) as votes
      from public.writing_challenge_entries e
      join public.writing_challenges c on c.id = e.challenge_id
     where now() >= c.voting_deadline
  ), ranked as (
    select k.*, rank() over (partition by k.challenge_id order by k.votes desc) as rnk from counted k
  )
  select challenge_id, book_id, author_id, votes from ranked where rnk = 1 and votes > 0;
$$;

-- Új kihívás meghirdetésekor mindenki értesítést kap.
create or replace function public.regelo_notify_challenge()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.community_notifications (recipient_id, actor_id, kind, payload)
  select p.user_id, new.created_by, 'CHALLENGE', jsonb_build_object('challenge_id', new.id, 'title', new.title)
    from public.public_profiles p
   where p.user_id <> new.created_by;
  return null;
end;
$$;
drop trigger if exists regelo_notify_challenge on public.writing_challenges;
create trigger regelo_notify_challenge after insert on public.writing_challenges
  for each row execute function public.regelo_notify_challenge();

-- =====================================================================================
-- 6) Aktivitásfolyam: a követett szerzők új fejezetei, új történetei, hírei, a követett olvasók
--    véleményei, a hivatalos hírek és az új írói kihívások – időrendben, lapozhatóan.
-- =====================================================================================
create or replace function public.regelo_activity_feed(p_before timestamptz default null, p_limit int default 30)
returns table (kind text, at timestamptz, actor_id uuid, book_id uuid, chapter_id uuid, ref_id uuid,
               title text, body text, stars int, age_rating text)
language sql stable security definer set search_path = public as $$
  with followed as (
    select f.followed_author_id as uid from public.user_follows f where f.follower_id = auth.uid()
  ),
  official as (select o.user_id as uid from public.regelo_official_accounts o),
  items as (
    select 'NEW_CHAPTER'::text as kind, c.published_at as at, b.author_id as actor_id, b.id as book_id,
           c.id as chapter_id, c.id as ref_id, c.title as title, b.title as body, null::int as stars, b.age_rating
      from public.chapters c join public.books b on b.id = c.book_id
     where c.published_at is not null and c.is_draft = false and b.publish_status = 'PUBLISHED'
       and b.author_id in (select uid from followed)
       and (b.published_at is null or c.published_at > b.published_at + interval '10 minutes')
    union all
    select 'NEW_BOOK', b.published_at, b.author_id, b.id, null, b.id, b.title, b.short_description, null, b.age_rating
      from public.books b
     where b.publish_status = 'PUBLISHED' and b.published_at is not null and b.author_id in (select uid from followed)
    union all
    select 'ANNOUNCEMENT', a.created_at, a.author_id, a.book_id, null, a.id, a.title, a.body, null, coalesce(b.age_rating, 'NONE')
      from public.author_announcements a left join public.books b on b.id = a.book_id
     where a.author_id in (select uid from followed union select uid from official)
    union all
    select 'REVIEW', r.created_at, r.user_id, b.id, null, b.id, b.title, r.review, r.stars::int, b.age_rating
      from public.book_ratings r join public.books b on b.id = r.book_id
     where r.user_id in (select uid from followed) and b.publish_status = 'PUBLISHED' and char_length(trim(r.review)) > 0
    union all
    select 'CHALLENGE', w.created_at, w.created_by, null, null, w.id, w.title, left(w.description, 300), null, 'NONE'
      from public.writing_challenges w
     where w.voting_deadline > now() - interval '30 days'
  )
  select i.kind, i.at, i.actor_id, i.book_id, i.chapter_id, i.ref_id, i.title, i.body, i.stars, i.age_rating
    from items i
   where auth.uid() is not null
     and (p_before is null or i.at < p_before)
     and i.actor_id is distinct from auth.uid()
     and not public.regelo_i_blocked(i.actor_id) and not public.regelo_blocked_me(i.actor_id)
   order by i.at desc
   limit least(greatest(coalesce(p_limit, 30), 1), 100);
$$;

-- =====================================================================================
-- 7) „Neked ajánljuk”: az olvasott, kedvelt, jól értékelt és elmentett könyvek műfajai,
--    témái és a követett szerzők alapján; előzmények nélkül a népszerű művek.
-- =====================================================================================
create or replace function public.regelo_recommended_books(p_limit int default 20, p_allow_adult boolean default false)
returns table (book_id uuid, reason text)
language sql stable security definer set search_path = public as $$
  with touched as (
    select p.book_id, 2.0::numeric as w from public.reader_progress p where p.user_id = auth.uid() and p.is_deleted = false
    union all select l.book_id, 3.0 from public.book_likes l where l.user_id = auth.uid()
    union all select r.book_id, (r.stars - 2.5)::numeric from public.book_ratings r where r.user_id = auth.uid()
    union all select s.book_id, 1.0 from public.reader_saved_books s where s.user_id = auth.uid() and s.is_saved
  ),
  genre_pref as (
    select b.genre, sum(t.w) as w from touched t join public.books b on b.id = t.book_id
     where b.genre <> '' group by b.genre having sum(t.w) > 0
  ),
  tag_pref as (
    select tag, sum(t.w) as w from touched t join public.books b on b.id = t.book_id, unnest(b.theme_tags) as tag
     group by tag having sum(t.w) > 0
  ),
  followed as (select f.followed_author_id as uid from public.user_follows f where f.follower_id = auth.uid()),
  likes as (select l.book_id, count(*) as n from public.book_likes l group by l.book_id),
  candidates as (
    select b.id, b.genre, b.theme_tags, b.author_id, b.view_count, b.updated_at,
           coalesce(l.n, 0) as likes, coalesce(rs.rating_avg, 0) as avg_rating
      from public.books b
      left join likes l on l.book_id = b.id
      left join public.book_rating_stats rs on rs.book_id = b.id
     where b.publish_status = 'PUBLISHED' and b.author_id <> auth.uid()
       and b.id not in (select t.book_id from touched t)
       and (p_allow_adult or b.age_rating <> '18+')
       and not public.regelo_i_blocked(b.author_id) and not public.regelo_blocked_me(b.author_id)
       and exists (select 1 from public.chapters c where c.book_id = b.id and c.is_draft = false)
  ),
  scored as (
    select c.id,
           coalesce((select g.w from genre_pref g where g.genre = c.genre), 0) * 3
           + coalesce((select sum(tp.w) from tag_pref tp where tp.tag = any (c.theme_tags)), 0)
           + case when c.author_id in (select uid from followed) then 4 else 0 end
           + ln(1 + c.likes) + c.avg_rating * 0.4 + ln(1 + greatest(c.view_count, 0)) * 0.3
           + case when c.updated_at > now() - interval '30 days' then 1 else 0 end as score,
           c.author_id in (select uid from followed) as followed_author,
           (select g.genre from genre_pref g where g.genre = c.genre) as liked_genre,
           (select tp.tag from tag_pref tp where tp.tag = any (c.theme_tags) order by tp.w desc limit 1) as liked_tag
      from candidates c
  )
  select s.id,
         case when s.followed_author then 'Követett szerzőtől'
              when s.liked_genre is not null then 'Mert szereted: ' || s.liked_genre
              when s.liked_tag is not null then 'Téma: ' || s.liked_tag
              else 'Népszerű a Regélőn' end
    from scored s
   where auth.uid() is not null
   order by s.score desc, s.id
   limit least(greatest(coalesce(p_limit, 20), 1), 50);
$$;

-- =====================================================================================
-- 8) Jelvények: nyilvános adatokból számolt mutatók egy felhasználóhoz
-- =====================================================================================
create or replace function public.regelo_user_badges(p_user uuid)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'published_books', (select count(*) from public.books where author_id = p_user and publish_status = 'PUBLISHED'),
    'completed_books', (select count(*) from public.books where author_id = p_user and publish_status = 'PUBLISHED' and is_completed),
    'published_chapters', (select count(*) from public.chapters c join public.books b on b.id = c.book_id
                            where b.author_id = p_user and b.publish_status = 'PUBLISHED' and c.is_draft = false),
    'published_words', (select coalesce(sum(c.word_count), 0) from public.chapters c join public.books b on b.id = c.book_id
                         where b.author_id = p_user and b.publish_status = 'PUBLISHED' and c.is_draft = false),
    'followers', (select count(*) from public.user_follows where followed_author_id = p_user),
    'likes_received', (select count(*) from public.chapter_likes l join public.chapters c on c.id = l.chapter_id
                        join public.books b on b.id = c.book_id where b.author_id = p_user),
    'comments_written', (select count(*) from public.chapter_comments where author_id = p_user and deleted_at is null),
    'reviews_written', (select count(*) from public.book_ratings where user_id = p_user and char_length(trim(review)) > 0),
    'challenge_entries', (select count(*) from public.writing_challenge_entries where author_id = p_user),
    'challenge_wins', (select count(*) from public.regelo_challenge_winners() w where w.author_id = p_user),
    'circles_created', (select count(*) from public.reading_circles where owner_id = p_user),
    'circle_posts', (select count(*) from public.reading_circle_posts where author_id = p_user and deleted_at is null)
  )
  where auth.uid() is not null;
$$;

revoke all on function public.regelo_activity_feed(timestamptz, int) from public, anon;
grant execute on function public.regelo_activity_feed(timestamptz, int) to authenticated;
revoke all on function public.regelo_recommended_books(int, boolean) from public, anon;
grant execute on function public.regelo_recommended_books(int, boolean) to authenticated;
revoke all on function public.regelo_user_badges(uuid) from public, anon;
grant execute on function public.regelo_user_badges(uuid) to authenticated;
revoke all on function public.regelo_challenge_results(uuid) from public, anon;
grant execute on function public.regelo_challenge_results(uuid) to authenticated;
revoke all on function public.regelo_challenge_winners() from public, anon;
grant execute on function public.regelo_challenge_winners() to authenticated;

grant select, insert, delete on public.reading_circles, public.reading_circle_members, public.reading_circle_posts,
  public.writing_challenge_entries, public.writing_challenge_votes to authenticated;
grant select, insert, update, delete on public.writing_challenges to authenticated;
revoke update on public.writing_challenge_entries, public.writing_challenge_votes from authenticated;

select 'ok' as out;
