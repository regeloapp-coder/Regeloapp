-- Regélő app v1.3 – hivatalos kiadványok, szerepkörök, rate limit, szövegtisztítás, adatexport, megőrzés.
-- Idempotens: többször is futtatható. Élesítve: 2026-10-15 (Supabase, pqmfezkvaslqedalwwnn).
-- A fájl az élő adatbázisból készült (pg_get_functiondef / pg_get_triggerdef / pg_policies), így pontosan azt
-- tartalmazza, ami fut. Jogosultsági tesztsor: supabase/tests/rls_battery_v1_3.sql (20/20 OK, 2026-10-15).
--
-- Tartalom:
--   1) Megkötések: OFFICIAL_UPDATE értesítéstípus, profillinkek szerveroldali ellenőrzése, reader_progress.chapter_id
--   2) Táblák: regelo_app_config, regelo_roles, regelo_official_books / _chapter_sources / _discussion_prompts /
--      _subscriptions, regelo_rate_events (+ RLS szabályok, indexek)
--   3) Függvények: szövegtisztítás, rate limit, export, szerepkör, szerveridő, hivatalos kiadványok,
--      értesítések, hírfolyam és ajánló (hivatalos könyvek kizárása), Könyvtár-áttekintés
--   4) Triggerek, 5) Cron feladatok, 6) Kezdő adatok (a négy hivatalos könyv és bevezető részeik)

-- =====================================================================================
-- 1) Megkötések
-- =====================================================================================
alter table public.reader_progress add column if not exists chapter_id uuid references public.chapters(id) on delete set null;
create index if not exists reader_progress_chapter_id_fk_idx on public.reader_progress (chapter_id);

alter table public.community_notifications drop constraint if exists community_notifications_kind_check;
alter table public.community_notifications add constraint community_notifications_kind_check check (kind = any (array[
  'FOLLOW','LIKE','COMMENT','REPLY','MESSAGE','ANNOUNCEMENT','RATING','NEW_CHAPTER','NEW_BOOK','CIRCLE_POST','CHALLENGE','OFFICIAL_UPDATE']));

-- FUNCTION regelo_valid_profile_links(p_links jsonb)  EXECUTE: -,authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_valid_profile_links(p_links jsonb)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$
    select jsonb_typeof(p_links) = 'array'
       and jsonb_array_length(p_links) <= 10
       and not exists (
           select 1
           from jsonb_array_elements(p_links) as e(item)
           where jsonb_typeof(e.item) <> 'object'
              or jsonb_typeof(e.item -> 'label') is distinct from 'string'
              or jsonb_typeof(e.item -> 'url') is distinct from 'string'
              or char_length(e.item ->> 'label') > 30
              or char_length(e.item ->> 'url') > 200
              or (e.item ->> 'url') !~* '^https?://'
       );
$function$;

alter table public.public_profiles drop constraint if exists public_profiles_links_valid;
alter table public.public_profiles add constraint public_profiles_links_valid check (public.regelo_valid_profile_links(links));

-- =====================================================================================
-- 2) Táblák (RLS: ahol nincs szabály, oda kliensről senki sem fér hozzá)
-- =====================================================================================
create table if not exists public.regelo_app_config (
  key text not null,
  value jsonb not null,
  updated_at timestamp with time zone not null default now(),
  constraint regelo_app_config_pkey PRIMARY KEY (key)
);
alter table public.regelo_app_config enable row level security;

create table if not exists public.regelo_roles (
  user_id uuid not null,
  role text not null,
  granted_at timestamp with time zone not null default now(),
  constraint regelo_roles_pkey PRIMARY KEY (user_id, role),
  constraint regelo_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint regelo_roles_role_check CHECK ((role = ANY (ARRAY['official_editor'::text, 'moderator'::text, 'admin'::text])))
);
alter table public.regelo_roles enable row level security;

create table if not exists public.regelo_official_books (
  book_id uuid not null,
  slug text not null,
  sort_order integer not null default 0,
  auto_enabled boolean not null default true,
  constraint regelo_official_books_slug_key UNIQUE (slug),
  constraint regelo_official_books_pkey PRIMARY KEY (book_id),
  constraint regelo_official_books_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  constraint regelo_official_books_slug_check CHECK ((slug = ANY (ARRAY['hirek'::text, 'kihivasok'::text, 'valogatas'::text, 'kavehaz'::text])))
);
alter table public.regelo_official_books enable row level security;

create table if not exists public.regelo_official_chapter_sources (
  chapter_id uuid not null,
  source_kind text not null,
  source_id text not null,
  constraint regelo_official_chapter_sources_source_kind_source_id_key UNIQUE (source_kind, source_id),
  constraint regelo_official_chapter_sources_pkey PRIMARY KEY (chapter_id),
  constraint regelo_official_chapter_sources_chapter_id_fkey FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
  constraint regelo_official_chapter_sources_source_kind_check CHECK ((source_kind = ANY (ARRAY['INTRO'::text, 'RELEASE_NOTES'::text, 'ANNOUNCEMENT'::text, 'CHALLENGE_OPEN'::text, 'CHALLENGE_VOTING'::text, 'CHALLENGE_RESULT'::text, 'WEEKLY_PICKS'::text, 'DISCUSSION'::text])))
);
alter table public.regelo_official_chapter_sources enable row level security;

create table if not exists public.regelo_official_discussion_prompts (
  id integer generated always as identity,
  question text not null,
  intro text not null default ''::text,
  used_at timestamp with time zone,
  constraint regelo_official_discussion_prompts_question_key UNIQUE (question),
  constraint regelo_official_discussion_prompts_pkey PRIMARY KEY (id),
  constraint regelo_official_discussion_prompts_question_check CHECK (((char_length(question) >= 5) AND (char_length(question) <= 200)))
);
alter table public.regelo_official_discussion_prompts enable row level security;

create table if not exists public.regelo_official_subscriptions (
  user_id uuid not null,
  book_id uuid not null,
  created_at timestamp with time zone not null default now(),
  constraint regelo_official_subscriptions_pkey PRIMARY KEY (user_id, book_id),
  constraint regelo_official_subscriptions_book_id_fkey FOREIGN KEY (book_id) REFERENCES regelo_official_books(book_id) ON DELETE CASCADE,
  constraint regelo_official_subscriptions_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);
alter table public.regelo_official_subscriptions enable row level security;

create table if not exists public.regelo_rate_events (
  user_id uuid,
  action text not null,
  at timestamp with time zone not null default now(),
  id bigint generated always as identity,
  constraint regelo_rate_events_pkey PRIMARY KEY (id)
);
alter table public.regelo_rate_events enable row level security;

create index if not exists regelo_official_subscriptions_book_id_fk_idx ON public.regelo_official_subscriptions USING btree (book_id);
create index if not exists regelo_rate_events_idx ON public.regelo_rate_events USING btree (action, user_id, at DESC);

drop policy if exists "app_config_read" on public.regelo_app_config;
create policy "app_config_read" on public.regelo_app_config as permissive for select to anon,authenticated
  using (true);
drop policy if exists "official_books_read" on public.regelo_official_books;
create policy "official_books_read" on public.regelo_official_books as permissive for select to anon,authenticated
  using (true);
drop policy if exists "official_subs_delete" on public.regelo_official_subscriptions;
create policy "official_subs_delete" on public.regelo_official_subscriptions as permissive for delete to authenticated
  using ((user_id = ( SELECT auth.uid() AS uid)));
drop policy if exists "official_subs_insert" on public.regelo_official_subscriptions;
create policy "official_subs_insert" on public.regelo_official_subscriptions as permissive for insert to authenticated
  with check ((user_id = ( SELECT auth.uid() AS uid)));
drop policy if exists "official_subs_read" on public.regelo_official_subscriptions;
create policy "official_subs_read" on public.regelo_official_subscriptions as permissive for select to authenticated
  using ((user_id = ( SELECT auth.uid() AS uid)));
drop policy if exists "roles_read_own" on public.regelo_roles;
create policy "roles_read_own" on public.regelo_roles as permissive for select to authenticated
  using ((user_id = ( SELECT auth.uid() AS uid)));

-- =====================================================================================
-- 3) Függvények
-- =====================================================================================
-- FUNCTION regelo_clean_text(t text, max_len integer)  EXECUTE: -,authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_clean_text(t text, max_len integer)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$
  select left(rtrim(regexp_replace(coalesce(t, ''),
    '[\x01-\x08\x0B\x0C\x0E-\x1F\x7F​‪-‮⁦-⁩]', '', 'g')), max_len)
$function$;

-- FUNCTION regelo_clean_guard()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_clean_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
begin
  case tg_table_name
    when 'chapter_comments' then
      new.body := regelo_clean_text(new.body, 5000);
      if char_length(btrim(new.body)) = 0 and new.deleted_at is null then
        raise exception 'Üres hozzászólás.' using errcode = '23514';
      end if;
    when 'reading_circle_posts' then
      new.body := regelo_clean_text(new.body, 4000);
      if char_length(btrim(new.body)) = 0 and new.deleted_at is null then
        raise exception 'Üres hozzászólás.' using errcode = '23514';
      end if;
    when 'community_messages' then
      new.body := regelo_clean_text(new.body, 5000);
      if char_length(btrim(new.body)) = 0 then
        raise exception 'Üres üzenet.' using errcode = '23514';
      end if;
    when 'author_announcements' then
      new.title := regelo_clean_text(new.title, 160);
      new.body := regelo_clean_text(new.body, 5000);
    when 'public_profiles' then
      new.display_name := regelo_clean_text(new.display_name, 80);
      new.bio := regelo_clean_text(new.bio, 2000);
    when 'books' then
      new.title := regelo_clean_text(new.title, 150);
      new.pen_name := regelo_clean_text(new.pen_name, 80);
      new.short_description := regelo_clean_text(new.short_description, 2000);
    when 'chapters' then
      new.title := regelo_clean_text(new.title, 120);
    else null;
  end case;
  return new;
end $function$;

-- FUNCTION regelo_hu_time(t timestamp with time zone)  EXECUTE: -,authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_hu_time(t timestamp with time zone)
 RETURNS text
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
  select to_char(t at time zone 'Europe/Budapest', 'YYYY. MM. DD. HH24:MI')
$function$;

-- FUNCTION regelo_md_title(t text)  EXECUTE: -,authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_md_title(t text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$
  select btrim(regexp_replace(coalesce(t, ''), '[\[\]\(\)\*_~]', '', 'g'))
$function$;

-- FUNCTION regelo_short_text(t text, max_len integer)  EXECUTE: -,authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_short_text(t text, max_len integer)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$
  select case when char_length(x) <= max_len then x
              else rtrim(regexp_replace(left(x, max_len), '\s+\S*$', ''), ' ,.;:–-') || '…' end
    from (select btrim(regexp_replace(coalesce(t, ''), '\s+', ' ', 'g')) as x) s
$function$;

-- FUNCTION regelo_valid_profile_links(p_links jsonb)  EXECUTE: -,authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_valid_profile_links(p_links jsonb)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$
    select jsonb_typeof(p_links) = 'array'
       and jsonb_array_length(p_links) <= 10
       and not exists (
           select 1
           from jsonb_array_elements(p_links) as e(item)
           where jsonb_typeof(e.item) <> 'object'
              or jsonb_typeof(e.item -> 'label') is distinct from 'string'
              or jsonb_typeof(e.item -> 'url') is distinct from 'string'
              or char_length(e.item ->> 'label') > 30
              or char_length(e.item ->> 'url') > 200
              or (e.item ->> 'url') !~* '^https?://'
       );
$function$;

-- FUNCTION regelo_has_role(p_role text)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_has_role(p_role text)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select exists (select 1 from public.regelo_roles where user_id = (select auth.uid()) and role = p_role)
$function$;

-- FUNCTION regelo_server_now()  EXECUTE: -,anon,authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_server_now()
 RETURNS timestamp with time zone
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$ select now() $function$;

-- FUNCTION regelo_rate_limit(p_action text, p_max integer, p_window interval)  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_rate_limit(p_action text, p_max integer, p_window interval)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_uid uuid := auth.uid();
  v_n int;
begin
  if v_uid is null then return; end if;   -- szerveroldali (cron, trigger) hívás
  if exists (select 1 from public.regelo_official_accounts o where o.user_id = v_uid) then return; end if;
  select count(*) into v_n from public.regelo_rate_events
   where action = p_action and user_id = v_uid and at > now() - p_window;
  if v_n >= p_max then
    raise exception 'Túl sok művelet rövid idő alatt. Várj egy kicsit, és próbáld újra.' using errcode = 'P0429';
  end if;
  insert into public.regelo_rate_events (user_id, action) values (v_uid, p_action);
end $function$;

-- FUNCTION regelo_rate_guard()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_rate_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_n int;
begin
  case tg_table_name
    when 'chapter_comments' then
      perform regelo_rate_limit('comment', 10, interval '1 minute');
      perform regelo_rate_limit('comment_day', 200, interval '1 day');
    when 'community_messages' then
      perform regelo_rate_limit('message', 20, interval '1 minute');
      perform regelo_rate_limit('message_day', 500, interval '1 day');
    when 'reading_circle_posts' then
      perform regelo_rate_limit('circle_post', 10, interval '1 minute');
    when 'reports', 'community_reports' then
      perform regelo_rate_limit('report', 10, interval '1 hour');
    when 'user_follows' then
      perform regelo_rate_limit('follow', 60, interval '1 hour');
    when 'app_error_reports' then
      if auth.uid() is null then
        -- Bejelentkezés nélkül: összesített korlát óránként.
        select count(*) into v_n from public.regelo_rate_events where action = 'error_anon' and at > now() - interval '1 hour';
        if v_n >= 100 then return null; end if;   -- csendben eldobjuk
        insert into public.regelo_rate_events (user_id, action) values (null, 'error_anon');
      else
        select count(*) into v_n from public.regelo_rate_events
         where action = 'error' and user_id = auth.uid() and at > now() - interval '1 hour';
        if v_n >= 30 then return null; end if;
        insert into public.regelo_rate_events (user_id, action) values (auth.uid(), 'error');
      end if;
    else null;
  end case;
  return new;
end $function$;

-- FUNCTION regelo_export_my_data()  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_export_my_data()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_uid uuid := auth.uid();
begin
  if v_uid is null then raise exception 'Bejelentkezés szükséges.' using errcode = '28000'; end if;
  perform public.regelo_rate_limit('export', 1, interval '1 hour');
  return jsonb_build_object(
    'generated_at', now(),
    'account', (select jsonb_build_object('id', u.id, 'email', u.email, 'created_at', u.created_at) from auth.users u where u.id = v_uid),
    'profile', (select to_jsonb(p) from public.public_profiles p where p.user_id = v_uid),
    'series', coalesce((select jsonb_agg(to_jsonb(s)) from public.series s where s.author_id = v_uid), '[]'),
    'books', coalesce((select jsonb_agg(to_jsonb(b)) from public.books b where b.author_id = v_uid), '[]'),
    'chapters', coalesce((select jsonb_agg(to_jsonb(c)) from public.chapters c join public.books b on b.id = c.book_id
                          where b.author_id = v_uid), '[]'),
    'announcements', coalesce((select jsonb_agg(to_jsonb(a)) from public.author_announcements a where a.author_id = v_uid), '[]'),
    'reading_progress', coalesce((select jsonb_agg(to_jsonb(r)) from public.reader_progress r where r.user_id = v_uid), '[]'),
    'saved_books', coalesce((select jsonb_agg(to_jsonb(s)) from public.reader_saved_books s where s.user_id = v_uid), '[]'),
    'library', coalesce((select jsonb_agg(to_jsonb(l)) from public.reader_library_items l where l.user_id = v_uid), '[]'),
    'book_likes', coalesce((select jsonb_agg(to_jsonb(l)) from public.book_likes l where l.user_id = v_uid), '[]'),
    'chapter_likes', coalesce((select jsonb_agg(to_jsonb(l)) from public.chapter_likes l where l.user_id = v_uid), '[]'),
    'ratings', coalesce((select jsonb_agg(to_jsonb(r)) from public.book_ratings r where r.user_id = v_uid), '[]'),
    'comments', coalesce((select jsonb_agg(to_jsonb(c)) from public.chapter_comments c where c.author_id = v_uid), '[]'),
    'follows', coalesce((select jsonb_agg(to_jsonb(f)) from public.user_follows f where f.follower_id = v_uid), '[]'),
    'blocked_users', coalesce((select jsonb_agg(to_jsonb(b)) from public.blocked_users b where b.user_id = v_uid), '[]'),
    'messages', coalesce((select jsonb_agg(jsonb_build_object('id', m.id, 'direction',
                          case when m.sender_id = v_uid then 'sent' else 'received' end,
                          'partner_id', case when m.sender_id = v_uid then m.recipient_id else m.sender_id end,
                          'body', m.body, 'created_at', m.created_at, 'read_at', m.read_at) order by m.created_at)
                          from public.community_messages m where m.sender_id = v_uid or m.recipient_id = v_uid), '[]'),
    'circle_memberships', coalesce((select jsonb_agg(to_jsonb(m)) from public.reading_circle_members m where m.user_id = v_uid), '[]'),
    'circle_posts', coalesce((select jsonb_agg(to_jsonb(p)) from public.reading_circle_posts p where p.author_id = v_uid), '[]'),
    'challenge_entries', coalesce((select jsonb_agg(to_jsonb(e)) from public.writing_challenge_entries e where e.author_id = v_uid), '[]'),
    'challenge_votes', coalesce((select jsonb_agg(to_jsonb(v)) from public.writing_challenge_votes v where v.voter_id = v_uid), '[]'),
    'reports_filed', coalesce((select jsonb_agg(jsonb_build_object('target_type', r.target_type, 'reason', r.reason, 'created_at', r.created_at))
                               from public.reports r where r.reporter_id = v_uid), '[]'),
    'official_subscriptions', coalesce((select jsonb_agg(to_jsonb(s)) from public.regelo_official_subscriptions s where s.user_id = v_uid), '[]'),
    'push_devices', (select count(*) from public.device_push_tokens t where t.user_id = v_uid)
  );
end $function$;

-- FUNCTION regelo_progress_chapter_guard()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_progress_chapter_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
begin
  if new.chapter_id is not null
     and new.chapter_index is distinct from old.chapter_index
     and new.chapter_id is not distinct from old.chapter_id
     and not exists (select 1 from (select c.id from public.chapters c
                                     where c.book_id = new.book_id and c.is_draft = false
                                     order by c.order_index offset greatest(new.chapter_index, 0) limit 1) x
                      where x.id = new.chapter_id) then
    new.chapter_id := null;
  end if;
  return new;
end $function$;

-- FUNCTION regelo_official_append(p_slug text, p_kind text, p_source_id text, p_title text, p_content text)  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_official_append(p_slug text, p_kind text, p_source_id text, p_title text, p_content text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_book uuid;
  v_next int;
  v_chapter uuid := gen_random_uuid();
  v_content text := left(coalesce(p_content, ''), 50000);
begin
  select book_id into v_book from public.regelo_official_books where slug = p_slug and auto_enabled;
  if v_book is null then return null; end if;
  -- A könyv sorának zárolása sorba állítja a párhuzamos hívásokat (sorszám és duplikáció ellen).
  perform 1 from public.books where id = v_book for update;
  if exists (select 1 from public.regelo_official_chapter_sources
             where source_kind = p_kind and source_id = p_source_id) then
    return null;
  end if;
  select coalesce(max(order_index), -1) + 1 into v_next from public.chapters where book_id = v_book;
  insert into public.chapters (id, book_id, title, content, word_count, order_index, is_draft, updated_at)
  values (v_chapter, v_book, left(coalesce(p_title, ''), 120), v_content,
          coalesce(array_length(regexp_split_to_array(btrim(v_content), '\s+'), 1), 0), v_next, false, now());
  insert into public.regelo_official_chapter_sources (chapter_id, source_kind, source_id)
  values (v_chapter, p_kind, p_source_id);
  return v_chapter;
end $function$;

-- FUNCTION regelo_official_from_announcement()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_official_from_announcement()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if exists (select 1 from public.regelo_official_accounts o where o.user_id = new.author_id) then
    perform public.regelo_official_append('hirek', 'ANNOUNCEMENT', new.id::text, new.title, new.body);
  end if;
  return null;
end $function$;

-- FUNCTION regelo_official_announcement_removed()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_official_announcement_removed()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  update public.chapters c
     set title = left('Visszavonva: ' || c.title, 120),
         content = 'Ezt a hírt a Regélő visszavonta.',
         updated_at = now()
   where c.id in (select s.chapter_id from public.regelo_official_chapter_sources s
                   where s.source_kind = 'ANNOUNCEMENT' and s.source_id = old.id::text)
     and c.title not like 'Visszavonva:%';
  return null;
end $function$;

-- FUNCTION regelo_official_book_guard()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_official_book_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if exists (select 1 from public.regelo_official_books o where o.book_id = new.id) then
    new.is_completed := false;
  end if;
  return new;
end $function$;

-- FUNCTION regelo_official_rating_guard()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_official_rating_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if exists (select 1 from public.regelo_official_books o where o.book_id = new.book_id) then
    raise exception 'A hivatalos kiadványokat nem lehet értékelni.' using errcode = '23514';
  end if;
  return new;
end $function$;

-- FUNCTION regelo_official_challenge_updates()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_official_challenge_updates()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  c record;
  v_list text;
begin
  perform set_config('regelo.system_push', '1', true);
  for c in select * from public.writing_challenges
            where voting_deadline > now() - interval '30 days' order by starts_at loop
    if c.starts_at <= now() then
      perform public.regelo_official_append('kihivasok', 'CHALLENGE_OPEN', c.id::text,
        'Új kihívás: ' || c.title,
        coalesce(nullif(btrim(c.description), ''), 'Új írói kihívás indult a Regélőn.') || E'\n\n' ||
        '- Nevezési határidő: ' || regelo_hu_time(c.submission_deadline) || E'\n' ||
        '- Szavazás: ' || regelo_hu_time(c.voting_deadline) || '-ig' || E'\n' ||
        case when c.max_words > 0 then '- Terjedelem: ' || c.min_words || '–' || c.max_words || E' szó\n' else '' end ||
        E'\nNevezni a Közösség › Kihívások fülön lehet, egy saját, nyilvános történettel.');
    end if;
    if c.submission_deadline <= now() then
      perform public.regelo_official_append('kihivasok', 'CHALLENGE_VOTING', c.id::text,
        'Indul a szavazás: ' || c.title,
        'A nevezés lezárult. ' ||
        (select count(*) from public.writing_challenge_entries e where e.challenge_id = c.id) ||
        ' történet versenyez. Szavazni ' || regelo_hu_time(c.voting_deadline) ||
        E'-ig lehet a Közösség › Kihívások fülön; mindenki egy szavazatot adhat, saját műre nem.');
    end if;
    if c.voting_deadline <= now() then
      select string_agg(format('%s. **[%s](regelo://book/%s)** – %s (%s szavazat)', r.rnk,
                               regelo_md_title(b.title), b.id, regelo_md_title(coalesce(nullif(b.pen_name, ''), p.display_name, 'ismeretlen szerző')), r.votes),
                        E'\n' order by r.rnk, b.title)
        into v_list
        from (select e.book_id, count(v.voter_id)::int as votes,
                     rank() over (order by count(v.voter_id) desc)::int as rnk
                from public.writing_challenge_entries e
                left join public.writing_challenge_votes v on v.challenge_id = e.challenge_id and v.book_id = e.book_id
               where e.challenge_id = c.id
               group by e.book_id) r
        join public.books b on b.id = r.book_id
        left join public.public_profiles p on p.user_id = b.author_id
       where r.rnk <= 3;
      perform public.regelo_official_append('kihivasok', 'CHALLENGE_RESULT', c.id::text,
        'Eredmény: ' || c.title,
        case when v_list is null then 'A kihívás lezárult, de nem érkezett nevezés. Legközelebb biztosan!'
             else E'A kihívás lezárult. Köszönjük minden nevezőnek és szavazónak!\n\n' || v_list end);
    end if;
  end loop;
end $function$;

-- FUNCTION regelo_official_weekly_picks()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_official_weekly_picks()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_week text := to_char(now() at time zone 'Europe/Budapest', 'IYYY-"W"IW');
  v_list text;
begin
  perform set_config('regelo.system_push', '1', true);
  with candidates as (
    select b.id, b.title, b.short_description, coalesce(nullif(b.pen_name, ''), p.display_name, 'ismeretlen szerző') as author,
           (b.published_at > now() - interval '7 days') as is_new,
           (select count(*) from public.book_views v where v.book_id = b.id and v.viewed_on > current_date - 7) as views
      from public.books b
      left join public.public_profiles p on p.user_id = b.author_id
     where b.publish_status = 'PUBLISHED' and b.age_rating <> '18+'
       and b.id not in (select book_id from public.regelo_official_books)
       and exists (select 1 from public.chapters c where c.book_id = b.id and c.is_draft = false)
  ), picked as (
    select * from candidates
     where is_new or not exists (select 1 from candidates where is_new)
     order by views desc, id limit 5
  )
  select string_agg(format(E'**[%s](regelo://book/%s)** – %s\n%s', regelo_md_title(title), id, regelo_md_title(author),
                           regelo_short_text(short_description, 220)),
                    E'\n\n' order by views desc, id)
    into v_list from picked;
  if v_list is null then return; end if;
  perform public.regelo_official_append('valogatas', 'WEEKLY_PICKS', v_week,
    'Heti válogatás – ' || to_char(now() at time zone 'Europe/Budapest', 'YYYY. MM. DD.'),
    E'Ezen a héten ezeket ajánljuk. A címre koppintva megnyílik a történet.\n\n' || v_list);
end $function$;

-- FUNCTION regelo_official_weekly_discussion()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_official_weekly_discussion()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_week text := to_char(now() at time zone 'Europe/Budapest', 'IYYY-"W"IW');
  q record;
begin
  perform set_config('regelo.system_push', '1', true);
  if exists (select 1 from public.regelo_official_chapter_sources where source_kind = 'DISCUSSION' and source_id = v_week) then
    return;
  end if;
  select * into q from public.regelo_official_discussion_prompts where used_at is null order by id limit 1 for update skip locked;
  if q.id is null then return; end if;
  if public.regelo_official_append('kavehaz', 'DISCUSSION', v_week, q.question,
       coalesce(nullif(btrim(q.intro), '') || E'\n\n', '') || q.question ||
       E'\n\nÍrd meg kommentben, mit gondolsz, és válaszolj a többiekre is!') is not null then
    update public.regelo_official_discussion_prompts set used_at = now() where id = q.id;
  end if;
end $function$;

-- FUNCTION regelo_notify_new_chapter()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_notify_new_chapter()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_book public.books%rowtype;
  v_slug text;
begin
  if new.published_at is null then return null; end if;
  if tg_op = 'UPDATE' and old.published_at is not null then return null; end if;
  -- A könyv első megjelenésekor a fejezetekről nem megy külön értesítés (az „új történet” szól róla).
  if coalesce(current_setting('regelo.bulk_publish', true), '') = '1' then return null; end if;
  select * into v_book from public.books where id = new.book_id;
  if v_book.id is null or v_book.publish_status <> 'PUBLISHED' then return null; end if;

  -- Hivatalos kiadvány: csak a feliratkozók kapnak értesítést (a Hírek könyvről a hír maga szól).
  select slug into v_slug from public.regelo_official_books where book_id = new.book_id;
  if v_slug is not null then
    if v_slug <> 'hirek' then
      insert into public.community_notifications (recipient_id, actor_id, kind, book_id, payload)
      select s.user_id, v_book.author_id, 'OFFICIAL_UPDATE', v_book.id,
             jsonb_build_object('chapter_id', new.id, 'chapter_title', new.title)
        from public.regelo_official_subscriptions s
       where s.book_id = v_book.id and s.user_id <> v_book.author_id;
    end if;
    return null;
  end if;

  if v_book.published_at is not null and v_book.published_at > now() - interval '10 minutes' then return null; end if;

  with recipients as (
    select f.follower_id as uid from public.user_follows f where f.followed_author_id = v_book.author_id
    union select s.user_id from public.reader_saved_books s where s.book_id = v_book.id and s.is_saved
    union select p.user_id from public.reader_progress p where p.book_id = v_book.id and p.is_deleted = false
  ),
  merged as (
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
$function$;

-- FUNCTION regelo_notify_new_book()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_notify_new_book()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if new.publish_status <> 'PUBLISHED' then return null; end if;
  if tg_op = 'UPDATE' and old.publish_status = 'PUBLISHED' then return null; end if;

  perform set_config('regelo.bulk_publish', '1', true);
  update public.chapters set published_at = now()
   where book_id = new.id and is_draft = false and published_at is null;
  perform set_config('regelo.bulk_publish', '', true);

  if tg_op = 'UPDATE' and old.published_at is not null then return null; end if; -- újrapublikálás
  if exists (select 1 from public.regelo_official_books o where o.book_id = new.id) then return null; end if;
  insert into public.community_notifications (recipient_id, actor_id, kind, book_id, payload)
  select f.follower_id, new.author_id, 'NEW_BOOK', new.id, '{}'::jsonb
    from public.user_follows f
   where f.followed_author_id = new.author_id and f.follower_id <> new.author_id;
  return null;
end;
$function$;

-- FUNCTION regelo_notify_announcement()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_notify_announcement()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if exists (select 1 from public.regelo_official_accounts o where o.user_id = new.author_id) then
    insert into public.community_notifications (recipient_id, actor_id, kind, announcement_id, book_id, payload)
    select p.user_id, new.author_id, 'ANNOUNCEMENT', new.id, new.book_id, jsonb_build_object('official', true)
    from public.public_profiles p
    where p.user_id <> new.author_id;
  else
    insert into public.community_notifications (recipient_id, actor_id, kind, announcement_id, book_id, payload)
    select followed.follower_id, new.author_id, 'ANNOUNCEMENT', new.id, new.book_id, '{}'::jsonb
    from public.user_follows followed
    where followed.followed_author_id = new.author_id;
  end if;
  return new;
end;
$function$;

-- FUNCTION regelo_activity_feed(p_before timestamp with time zone, p_limit integer)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_activity_feed(p_before timestamp with time zone DEFAULT NULL::timestamp with time zone, p_limit integer DEFAULT 30)
 RETURNS TABLE(kind text, at timestamp with time zone, actor_id uuid, book_id uuid, chapter_id uuid, ref_id uuid, title text, body text, stars integer, age_rating text)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  with followed as (
    select f.followed_author_id as uid from public.user_follows f where f.follower_id = auth.uid()
  ),
  official as (select o.user_id as uid from public.regelo_official_accounts o),
  official_books as (select ob.book_id from public.regelo_official_books ob),
  items as (
    select 'NEW_CHAPTER'::text as kind, c.published_at as at, b.author_id as actor_id, b.id as book_id,
           c.id as chapter_id, c.id as ref_id, c.title as title, b.title as body, null::int as stars, b.age_rating
      from public.chapters c join public.books b on b.id = c.book_id
     where c.published_at is not null and c.is_draft = false and b.publish_status = 'PUBLISHED'
       and b.author_id in (select uid from followed)
       and b.id not in (select book_id from official_books)
       and (b.published_at is null or c.published_at > b.published_at + interval '10 minutes')
    union all
    select 'NEW_BOOK', b.published_at, b.author_id, b.id, null, b.id, b.title, b.short_description, null, b.age_rating
      from public.books b
     where b.publish_status = 'PUBLISHED' and b.published_at is not null and b.author_id in (select uid from followed)
       and b.id not in (select book_id from official_books)
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
$function$;

-- FUNCTION regelo_recommended_books(p_limit integer, p_allow_adult boolean)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_recommended_books(p_limit integer DEFAULT 20, p_allow_adult boolean DEFAULT false)
 RETURNS TABLE(book_id uuid, reason text)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
       and b.id not in (select ob.book_id from public.regelo_official_books ob)
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
$function$;

-- FUNCTION regelo_valid_profile_links(p_links jsonb)  EXECUTE: -,authenticated,service_role  (lásd fent)

-- A Könyvtár „A Regélőtől” sávjának adatai egyetlen hívással: a hivatalos könyvek, a részek száma,
-- a saját olvasási pozíció, az olvasatlan részek száma és a feliratkozás. A hívó jogaival fut
-- (security invoker), így csak a publikált részeket és a saját pozícióját látja.
create or replace function public.regelo_official_overview()
returns table (
  book_id uuid, slug text, sort_order integer, title text, short_description text,
  cover_url text, cover_color_hex bigint,
  chapter_count integer, first_chapter_id uuid, last_chapter_id uuid, last_published_at timestamptz,
  my_chapter_id uuid, my_chapter_index integer, my_percent integer,
  unread integer, subscribed boolean
)
language sql
stable
security invoker
set search_path = public
as $$
  with ob as (
    select o.book_id, o.slug, o.sort_order, b.title, b.short_description, b.cover_url, b.cover_color_hex
      from public.regelo_official_books o
      join public.books b on b.id = o.book_id
     where b.publish_status = 'PUBLISHED'
  ), ch as (
    select c.book_id, c.id, c.published_at,
           (row_number() over (partition by c.book_id order by c.order_index, c.id) - 1)::int as pos,
           (count(*) over (partition by c.book_id))::int as cnt
      from public.chapters c
     where c.book_id in (select ob.book_id from ob) and c.is_draft = false
  ), pr as (
    select p.book_id, p.chapter_id, p.chapter_index, p.percent
      from public.reader_progress p
     where p.user_id = (select auth.uid()) and p.is_deleted = false
       and p.book_id in (select ob.book_id from ob)
  ), mypos as (
    -- A pozíció a rész azonosítója szerint; régi (azonosító nélküli) adatnál a sorszám szerint.
    select pr.book_id,
           coalesce((select ch.pos from ch where ch.book_id = pr.book_id and ch.id = pr.chapter_id),
                    pr.chapter_index) as pos
      from pr
  )
  select ob.book_id, ob.slug, ob.sort_order, ob.title, ob.short_description, ob.cover_url, ob.cover_color_hex,
         coalesce((select max(ch.cnt) from ch where ch.book_id = ob.book_id), 0),
         (select ch.id from ch where ch.book_id = ob.book_id order by ch.pos limit 1),
         (select ch.id from ch where ch.book_id = ob.book_id order by ch.pos desc limit 1),
         (select max(ch.published_at) from ch where ch.book_id = ob.book_id),
         pr.chapter_id, pr.chapter_index, pr.percent,
         case when mp.book_id is null then coalesce((select max(ch.cnt) from ch where ch.book_id = ob.book_id), 0)
              else (select count(*) from ch where ch.book_id = ob.book_id and ch.pos > mp.pos)::int
         end,
         exists (select 1 from public.regelo_official_subscriptions s
                  where s.user_id = (select auth.uid()) and s.book_id = ob.book_id)
    from ob
    left join pr on pr.book_id = ob.book_id
    left join mypos mp on mp.book_id = ob.book_id
   order by ob.sort_order
$$;
revoke execute on function public.regelo_official_overview() from public, anon;
grant execute on function public.regelo_official_overview() to authenticated;

-- Jogosultságok: a trigger- és belső függvényeket kliensről senki sem hívhatja; az RPC-k csak bejelentkezve.
do $$
declare f record;
begin
  for f in select p.oid::regprocedure as sig from pg_proc p
            where p.pronamespace = 'public'::regnamespace
              and p.proname in ('regelo_clean_guard','regelo_rate_guard','regelo_rate_limit','regelo_progress_chapter_guard',
                                'regelo_official_append','regelo_official_from_announcement','regelo_official_announcement_removed',
                                'regelo_official_book_guard','regelo_official_rating_guard','regelo_official_challenge_updates',
                                'regelo_official_weekly_picks','regelo_official_weekly_discussion','regelo_notify_new_chapter',
                                'regelo_notify_new_book','regelo_notify_announcement')
  loop execute format('revoke execute on function %s from public, anon, authenticated', f.sig); end loop;
  for f in select p.oid::regprocedure as sig from pg_proc p
            where p.pronamespace = 'public'::regnamespace
              and p.proname in ('regelo_export_my_data','regelo_has_role','regelo_activity_feed','regelo_recommended_books','regelo_official_overview')
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;
grant execute on function public.regelo_server_now() to anon, authenticated;

-- =====================================================================================
-- 4) Triggerek
-- =====================================================================================
drop trigger if exists regelo_rate_guard on public.app_error_reports;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.app_error_reports FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_clean_guard on public.author_announcements;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF title, body ON public.author_announcements FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_official_announcement_removed on public.author_announcements;
CREATE TRIGGER regelo_official_announcement_removed AFTER DELETE ON public.author_announcements FOR EACH ROW EXECUTE FUNCTION regelo_official_announcement_removed();
drop trigger if exists regelo_official_from_announcement on public.author_announcements;
CREATE TRIGGER regelo_official_from_announcement AFTER INSERT ON public.author_announcements FOR EACH ROW EXECUTE FUNCTION regelo_official_from_announcement();
drop trigger if exists regelo_official_rating_guard on public.book_ratings;
CREATE TRIGGER regelo_official_rating_guard BEFORE INSERT OR UPDATE ON public.book_ratings FOR EACH ROW EXECUTE FUNCTION regelo_official_rating_guard();
drop trigger if exists regelo_clean_guard on public.books;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF title, pen_name, short_description ON public.books FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_official_book_guard on public.books;
CREATE TRIGGER regelo_official_book_guard BEFORE UPDATE ON public.books FOR EACH ROW EXECUTE FUNCTION regelo_official_book_guard();
drop trigger if exists regelo_clean_guard on public.chapter_comments;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF body ON public.chapter_comments FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_rate_guard on public.chapter_comments;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.chapter_comments FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_clean_guard on public.chapters;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF title ON public.chapters FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_clean_guard on public.community_messages;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF body ON public.community_messages FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_rate_guard on public.community_messages;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.community_messages FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_rate_guard on public.community_reports;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.community_reports FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_clean_guard on public.public_profiles;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF display_name, bio ON public.public_profiles FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_progress_chapter_guard on public.reader_progress;
CREATE TRIGGER regelo_progress_chapter_guard BEFORE UPDATE ON public.reader_progress FOR EACH ROW EXECUTE FUNCTION regelo_progress_chapter_guard();
drop trigger if exists regelo_clean_guard on public.reading_circle_posts;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF body ON public.reading_circle_posts FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_rate_guard on public.reading_circle_posts;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.reading_circle_posts FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_rate_guard on public.reports;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.reports FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_rate_guard on public.user_follows;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.user_follows FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();

-- =====================================================================================
-- 5) Cron feladatok (pg_cron; a név szerinti ütemezés felülírja a régit)
-- =====================================================================================
select cron.schedule('regelo-official-challenges', '*/10 * * * *', $cron$select public.regelo_official_challenge_updates()$cron$);
select cron.schedule('regelo-official-weekly-picks', '0 6 * * 1', $cron$select public.regelo_official_weekly_picks()$cron$);
select cron.schedule('regelo-official-discussion', '0 6 * * 3', $cron$select public.regelo_official_weekly_discussion()$cron$);
select cron.schedule('regelo-retention', '30 2 * * *', $cron$
  delete from public.app_error_reports where created_at < now() - interval '90 days';
  -- Törölt hozzászólások: 30 nap után a válasz nélküliek törlődnek, a többiek szövege kiürül.
  -- Nyitott bejelentés esetén a moderáció lezárásáig minden marad.
  delete from public.chapter_comments c
   where c.deleted_at < now() - interval '30 days'
     and not exists (select 1 from public.chapter_comments r where r.parent_id = c.id)
     and not exists (select 1 from public.community_reports cr
                      where cr.comment_id = c.id and cr.status in ('OPEN', 'REVIEWING'))
     and not exists (select 1 from public.reports rp where rp.target_id = c.id and not rp.resolved);
  update public.chapter_comments c set body = '[törölve]'
   where c.deleted_at < now() - interval '30 days' and c.body <> '[törölve]'
     and not exists (select 1 from public.community_reports cr
                      where cr.comment_id = c.id and cr.status in ('OPEN', 'REVIEWING'))
     and not exists (select 1 from public.reports rp where rp.target_id = c.id and not rp.resolved);
  update public.reading_circle_posts p set body = '[törölve]'
   where p.deleted_at < now() - interval '30 days' and p.body <> '[törölve]'
     and not exists (select 1 from public.reports rp where rp.target_id = p.id and not rp.resolved);
  delete from public.community_notifications where read_at is not null and created_at < now() - interval '180 days';
  delete from public.device_push_tokens where updated_at < now() - interval '270 days';
  delete from public.book_views where viewed_on < current_date - 400;
  delete from public.reports where resolved and created_at < now() - interval '1 year';
  delete from public.community_reports where status in ('RESOLVED', 'REJECTED') and created_at < now() - interval '1 year';
  delete from public.regelo_rate_events where at < now() - interval '2 days';
$cron$);

-- =====================================================================================
-- 6) Kezdő adatok (csak ha még nincsenek meg)
-- =====================================================================================
insert into public.regelo_app_config(key, value) values ('official_strip_enabled', 'true'::jsonb), ('min_supported_version_code', '121'::jsonb) on conflict (key) do nothing;
insert into public.regelo_roles(user_id, role) values ('72093b95-8deb-49c7-8bea-8dd74b35111e', 'official_editor') on conflict do nothing;
insert into public.books(id, author_id, title, pen_name, short_description, cover_color_hex, is_completed, age_rating, publish_status)
  values ('0f1c1a10-0000-4000-8000-000000000001', '72093b95-8deb-49c7-8bea-8dd74b35111e', 'Regélő Hírek & Újdonságok', 'Regélő', 'A Regélő hivatalos hírei: új funkciók, változások és fontos tudnivalók, mindig egy helyen.', 4281023061, false, 'NONE', 'PUBLISHED')
  on conflict (id) do nothing;
insert into public.regelo_official_books(book_id, slug, sort_order) values ('0f1c1a10-0000-4000-8000-000000000001', 'hirek', 1) on conflict do nothing;
insert into public.books(id, author_id, title, pen_name, short_description, cover_color_hex, is_completed, age_rating, publish_status)
  values ('0f1c1a10-0000-4000-8000-000000000002', '72093b95-8deb-49c7-8bea-8dd74b35111e', 'Közösségi Kihívások', 'Regélő', 'Minden írói kihívás egy helyen: meghirdetés, szavazás és eredményhirdetés.', 4281023061, false, 'NONE', 'PUBLISHED')
  on conflict (id) do nothing;
insert into public.regelo_official_books(book_id, slug, sort_order) values ('0f1c1a10-0000-4000-8000-000000000002', 'kihivasok', 2) on conflict do nothing;
insert into public.books(id, author_id, title, pen_name, short_description, cover_color_hex, is_completed, age_rating, publish_status)
  values ('0f1c1a10-0000-4000-8000-000000000003', '72093b95-8deb-49c7-8bea-8dd74b35111e', 'Heti válogatás', 'Regélő', 'Minden hétfőn öt friss történet a Regélőről, az olvasások és az értékelések alapján.', 4281023061, false, 'NONE', 'PUBLISHED')
  on conflict (id) do nothing;
insert into public.regelo_official_books(book_id, slug, sort_order) values ('0f1c1a10-0000-4000-8000-000000000003', 'valogatas', 3) on conflict do nothing;
insert into public.books(id, author_id, title, pen_name, short_description, cover_color_hex, is_completed, age_rating, publish_status)
  values ('0f1c1a10-0000-4000-8000-000000000004', '72093b95-8deb-49c7-8bea-8dd74b35111e', 'Kávéház', 'Regélő', 'Heti beszélgetésindító kérdések írásról és olvasásról. Szólj hozzá kommentben!', 4281023061, false, 'NONE', 'PUBLISHED')
  on conflict (id) do nothing;
insert into public.regelo_official_books(book_id, slug, sort_order) values ('0f1c1a10-0000-4000-8000-000000000004', 'kavehaz', 4) on conflict do nothing;
insert into public.chapters(id, book_id, title, content, order_index, is_draft)
  values ('1de557ee-ab50-4fee-9374-a56c9fc4e8f6', '0f1c1a10-0000-4000-8000-000000000001', 'Üdv a Regélő Híreknél!', 'Ebben a könyvben gyűjtjük a Regélő hivatalos híreit: új funkciókat, változásokat és fontos tudnivalókat. Minden új hír új részként jelenik meg, így egy helyen visszaolvashatod, mi történt.

A hírekről továbbra is értesítést kapsz, ehhez a könyvhöz nem kell külön feliratkoznod.', 0, false)
  on conflict (id) do nothing;
insert into public.regelo_official_chapter_sources(chapter_id, source_kind, source_id) values ('1de557ee-ab50-4fee-9374-a56c9fc4e8f6', 'INTRO', 'hirek') on conflict do nothing;
insert into public.chapters(id, book_id, title, content, order_index, is_draft)
  values ('f5b8b52e-3211-42b4-be2c-28a7cef93fa6', '0f1c1a10-0000-4000-8000-000000000001', 'Mi újság az 1.3-as verzióban?', '- **A Regélőtől:** a Könyvtárban új sáv mutatja a hivatalos kiadványokat (Hírek, Kihívások, Heti válogatás, Kávéház). Ha nem kéred, a sáv menüjében elrejtheted.
- **Naprakész vagy:** ha egy még folyamatban lévő történet végére érsz, az app nem számolja kiolvasottnak. Ha új rész érkezik, a történet visszakerül a Folytatásba.
- **Pontosabb kihívások:** a nevezés és a szavazás a szerver ideje szerint nyílik és zárul, nyitott képernyőn is.
- **Adataid:** a Beállítások › Adatvédelem alatt egy fájlban letöltheted minden adatodat.
- **Biztonság:** a bejelentkezésed titkosítva tárolódik a telefonon, és az app nem nyit meg gyanús linket.
- **Stabilabb üzenetek:** ha az élő kapcsolat akadozik, a megnyitott beszélgetés akkor is frissül.', 1, false)
  on conflict (id) do nothing;
insert into public.regelo_official_chapter_sources(chapter_id, source_kind, source_id) values ('f5b8b52e-3211-42b4-be2c-28a7cef93fa6', 'RELEASE_NOTES', '1.3.0') on conflict do nothing;
insert into public.chapters(id, book_id, title, content, order_index, is_draft)
  values ('26fc51a8-7508-4b44-84bd-9ada44b4d0f4', '0f1c1a10-0000-4000-8000-000000000002', 'Így működnek a közösségi kihívások', 'Itt jelenik meg minden írói kihívás: a meghirdetés, a szavazás indulása és az eredmény.

Nevezni egy saját, nyilvános történettel lehet a Közösség › Kihívások fülön. Szavazni a szavazási időszakban lehet, saját műre nem.

Ha minden új részről értesítést szeretnél, kapcsold be a könyv adatlapján az „Értesíts az új részekről” kapcsolót.', 0, false)
  on conflict (id) do nothing;
insert into public.regelo_official_chapter_sources(chapter_id, source_kind, source_id) values ('26fc51a8-7508-4b44-84bd-9ada44b4d0f4', 'INTRO', 'kihivasok') on conflict do nothing;
insert into public.chapters(id, book_id, title, content, order_index, is_draft)
  values ('abafe8d2-f66d-42d9-8678-1101a844247e', '0f1c1a10-0000-4000-8000-000000000003', 'Mi ez a válogatás?', 'Minden hétfőn öt friss, nyilvános történetet ajánlunk a Regélőről, az olvasások és az értékelések alapján. A címre koppintva rögtön megnyílik a történet.

18+ művek nem kerülnek bele.', 0, false)
  on conflict (id) do nothing;
insert into public.regelo_official_chapter_sources(chapter_id, source_kind, source_id) values ('abafe8d2-f66d-42d9-8678-1101a844247e', 'INTRO', 'valogatas') on conflict do nothing;
insert into public.chapters(id, book_id, title, content, order_index, is_draft)
  values ('de7c030f-d9fb-4b89-93b4-7b06fa1cb4c8', '0f1c1a10-0000-4000-8000-000000000004', 'Üdv a Kávéházban!', 'Minden szerdán új beszélgetésindító kérdés érkezik írásról és olvasásról. Szólj hozzá kommentben, válaszolj másoknak, és figyelj egymásra: itt is érvényesek a közösségi szabályok.', 0, false)
  on conflict (id) do nothing;
insert into public.regelo_official_chapter_sources(chapter_id, source_kind, source_id) values ('de7c030f-d9fb-4b89-93b4-7b06fa1cb4c8', 'INTRO', 'kavehaz') on conflict do nothing;
insert into public.regelo_official_discussion_prompts(id, question, intro) overriding system value values (1, 'Melyik volt az első könyv, amelyik igazán magával ragadott?', '') on conflict do nothing;
insert into public.regelo_official_discussion_prompts(id, question, intro) overriding system value values (2, 'Hogyan születik nálad egy új történet ötlete?', '') on conflict do nothing;
insert into public.regelo_official_discussion_prompts(id, question, intro) overriding system value values (3, 'Melyik szereplőt szeretnéd egyszer személyesen megismerni, és miért?', '') on conflict do nothing;
insert into public.regelo_official_discussion_prompts(id, question, intro) overriding system value values (4, 'Tervezve írsz, vagy hagyod, hogy a történet vigyen?', '') on conflict do nothing;
insert into public.regelo_official_discussion_prompts(id, question, intro) overriding system value values (5, 'Mi az, ami miatt félbehagysz egy könyvet?', '') on conflict do nothing;
insert into public.regelo_official_discussion_prompts(id, question, intro) overriding system value values (6, 'Melyik műfajt nem próbáltad még, de szívesen írnál benne?', '') on conflict do nothing;
insert into public.regelo_official_discussion_prompts(id, question, intro) overriding system value values (7, 'Hol és mikor írsz vagy olvasol a legszívesebben?', '') on conflict do nothing;
insert into public.regelo_official_discussion_prompts(id, question, intro) overriding system value values (8, 'Melyik könyv ért véget úgy, hogy még napokig rá gondoltál?', '') on conflict do nothing;
insert into public.regelo_official_discussion_prompts(id, question, intro) overriding system value values (9, 'Milyen tanácsot adnál annak, aki most kezd írni?', '') on conflict do nothing;
insert into public.regelo_official_discussion_prompts(id, question, intro) overriding system value values (10, 'Melyik magyar szerzőt ajánlanád mindenkinek?', '') on conflict do nothing;

-- =====================================================================================
-- Visszavonás (csak szükség esetén, kézzel):
-- select cron.unschedule(jobname) from cron.job where jobname in ('regelo-official-challenges','regelo-official-weekly-picks','regelo-official-discussion','regelo-retention');
-- update public.regelo_official_books set auto_enabled = false;          -- vészkapcsoló: nincs több automatikus rész
-- update public.regelo_app_config set value = 'false' where key = 'official_strip_enabled';   -- a sáv elrejtése az appban
-- drop trigger if exists regelo_rate_guard on public.chapter_comments;  -- (táblánként) rate limit kikapcsolása
-- drop function if exists public.regelo_official_overview();
