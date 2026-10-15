-- Regélő app v1.0 (újraépítés) – kiegészítő migráció. Idempotens: többször is futtatható.

-- 1) Fejezet-piszkozatok: egy már publikált könyvben is lehet még nem publikált fejezet.
alter table public.chapters add column if not exists is_draft boolean not null default false;

drop policy if exists chapters_select on public.chapters;
create policy chapters_select on public.chapters for select using (
  exists (
    select 1 from public.books b
    where b.id = chapters.book_id
      and (b.author_id = auth.uid() or (b.publish_status = 'PUBLISHED' and chapters.is_draft = false))
  )
);

-- 2) Önkéntes borravaló: a szerző PayPal.me felhasználóneve (csak link, nincs pénzkezelés az appban).
alter table public.public_profiles add column if not exists paypal_me text not null default '';
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'public_profiles_paypal_me_check') then
    alter table public.public_profiles add constraint public_profiles_paypal_me_check
      check (char_length(paypal_me) <= 60 and paypal_me ~ '^[A-Za-z0-9._-]*$');
  end if;
end $$;

-- 3) Nyilvános olvasási listák: a COLLECTION elemek, amelyeknél payload.public = true, mások számára is láthatók.
drop policy if exists "Public reading lists are visible" on public.reader_library_items;
create policy "Public reading lists are visible" on public.reader_library_items for select to authenticated
  using (kind = 'COLLECTION' and is_deleted = false and coalesce((payload ->> 'public')::boolean, false));

-- 4) Valós idejű üzenetek és értesítések.
do $$ begin
  if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    create publication supabase_realtime;
  end if;
  if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and tablename='community_messages') then
    alter publication supabase_realtime add table public.community_messages;
  end if;
  if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and tablename='community_notifications') then
    alter publication supabase_realtime add table public.community_notifications;
  end if;
end $$;

-- 5) Gyorsító indexek a gyakori lekérdezésekhez.
create index if not exists books_status_updated_idx on public.books (publish_status, updated_at desc);
create index if not exists books_author_idx on public.books (author_id);
create index if not exists chapters_book_order_idx on public.chapters (book_id, order_index);
create index if not exists chapter_comments_chapter_idx on public.chapter_comments (chapter_id, paragraph_index);
create index if not exists community_messages_pair_idx on public.community_messages (sender_id, recipient_id, created_at desc);
create index if not exists community_messages_recipient_idx on public.community_messages (recipient_id, created_at desc);
create index if not exists community_notifications_recipient_idx on public.community_notifications (recipient_id, created_at desc);

select 'ok' as out;

-- 6) Moderátorok elrejthetik (soft delete) a jelentett kommenteket, és láthatják a jelentett kommentet.
drop policy if exists chapter_comments_moderate on public.chapter_comments;
create policy chapter_comments_moderate on public.chapter_comments for update to authenticated
  using (public.regelo_is_moderator()) with check (public.regelo_is_moderator());
drop policy if exists chapter_comments_read_moderators on public.chapter_comments;
create policy chapter_comments_read_moderators on public.chapter_comments for select to authenticated
  using (public.regelo_is_moderator());

-- 7) Alkalmazás-hibanapló: a telefonon fellépő (nem hálózati) hibák ide kerülnek, hogy a fejlesztő lássa.
create table if not exists public.app_error_reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete cascade,
  app_version text not null default '',
  device text not null default '',
  context text not null default '',
  message text not null default '',
  stack text not null default '',
  created_at timestamptz not null default now()
);
alter table public.app_error_reports enable row level security;
drop policy if exists app_error_reports_insert on public.app_error_reports;
create policy app_error_reports_insert on public.app_error_reports for insert to authenticated, anon
  with check ((user_id is null or user_id = auth.uid()) and char_length(message) <= 4000 and char_length(stack) <= 20000);
create index if not exists app_error_reports_created_idx on public.app_error_reports (created_at desc);
