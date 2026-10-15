-- Regélő app v1.2.1 – átvizsgálás utáni javítások. Idempotens: többször is futtatható.
--  1) Jogosultságok szigorítása (Supabase Security Advisor)
--  2) Sorszintű szabályok gyorsítása: auth.uid() egyszeri kiértékelése (Performance Advisor)
--  3) Hiányzó idegenkulcs-indexek, duplikált indexek törlése
--  4) Időzített publikálás a szerveren (pg_cron), push értesítéssel

-- =====================================================================================
-- 1) Jogosultságok
-- =====================================================================================

-- Triggerfüggvények: csak triggerként futhatnak, közvetlenül (RPC-n át) senki ne hívhassa őket.
-- (A trigger lefutásához nem kell EXECUTE jog a műveletet végző szerepkörnek.)
do $$
declare f record;
begin
  for f in
    select p.oid::regprocedure as sig
      from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.prorettype = 'trigger'::regtype and p.proname like 'regelo%'
  loop
    execute format('revoke execute on function %s from public, anon, authenticated', f.sig);
  end loop;
end $$;

-- Az app RPC-i és a sorszintű szabályok segédfüggvényei: csak bejelentkezett felhasználónak.
do $$
declare f record;
begin
  for f in
    select p.oid::regprocedure as sig
      from pg_proc p join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.prorettype <> 'trigger'::regtype
       and p.proname in (
         'delete_own_account', 'regelo_activity_feed', 'regelo_book_stats', 'regelo_challenge_results',
         'regelo_challenge_winners', 'regelo_recommended_books', 'regelo_record_book_view',
         'regelo_register_push_token', 'regelo_user_badges', 'regelo_search_profiles',
         'regelo_is_moderator', 'regelo_is_circle_owner', 'regelo_is_circle_member', 'regelo_can_join_circle',
         'regelo_i_blocked', 'regelo_blocked_me', 'regelo_is_blocked'
       )
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;

alter function public.regelo_profile_username(uuid, text) set search_path = public;

-- =====================================================================================
-- 2) auth.uid() egyszeri kiértékelése a szabályokban: (select auth.uid())
-- =====================================================================================
do $$
declare
  p record;
  q text;
  w text;
begin
  for p in
    select tablename, policyname, cmd, qual, with_check
      from pg_policies
     where schemaname = 'public'
       and (coalesce(qual, '') like '%auth.uid()%' or coalesce(with_check, '') like '%auth.uid()%')
  loop
    q := replace(replace(p.qual, '( SELECT auth.uid() AS uid)', 'auth.uid()'), 'auth.uid()', '(select auth.uid())');
    w := replace(replace(p.with_check, '( SELECT auth.uid() AS uid)', 'auth.uid()'), 'auth.uid()', '(select auth.uid())');
    if p.cmd in ('SELECT', 'DELETE') then
      execute format('alter policy %I on public.%I using (%s)', p.policyname, p.tablename, q);
    elsif p.cmd = 'INSERT' then
      execute format('alter policy %I on public.%I with check (%s)', p.policyname, p.tablename, w);
    else
      if q is not null and w is not null then
        execute format('alter policy %I on public.%I using (%s) with check (%s)', p.policyname, p.tablename, q, w);
      elsif q is not null then
        execute format('alter policy %I on public.%I using (%s)', p.policyname, p.tablename, q);
      elsif w is not null then
        execute format('alter policy %I on public.%I with check (%s)', p.policyname, p.tablename, w);
      end if;
    end if;
  end loop;
end $$;

-- =====================================================================================
-- 3) Indexek
-- =====================================================================================
create index if not exists app_error_reports_user_id_fk_idx on public.app_error_reports (user_id);
create index if not exists author_announcements_book_id_fk_idx on public.author_announcements (book_id);
create index if not exists book_likes_book_id_fk_idx on public.book_likes (book_id);
create index if not exists book_views_book_id_fk_idx on public.book_views (book_id);
create index if not exists books_series_id_fk_idx on public.books (series_id);
create index if not exists chapter_comments_author_id_fk_idx on public.chapter_comments (author_id);
create index if not exists chapter_comments_parent_id_fk_idx on public.chapter_comments (parent_id);
create index if not exists chapter_likes_user_id_fk_idx on public.chapter_likes (user_id);
create index if not exists characters_book_id_fk_idx on public.characters (book_id);
create index if not exists comments_author_id_fk_idx on public.comments (author_id);
create index if not exists community_notifications_actor_id_fk_idx on public.community_notifications (actor_id);
create index if not exists community_notifications_book_id_fk_idx on public.community_notifications (book_id);
create index if not exists community_notifications_comment_id_fk_idx on public.community_notifications (comment_id);
create index if not exists community_notifications_message_id_fk_idx on public.community_notifications (message_id);
create index if not exists community_reports_comment_id_fk_idx on public.community_reports (comment_id);
create index if not exists community_reports_reported_user_id_fk_idx on public.community_reports (reported_user_id);
create index if not exists community_reports_reporter_id_fk_idx on public.community_reports (reporter_id);
create index if not exists favorites_book_id_fk_idx on public.favorites (book_id);
create index if not exists follows_author_id_fk_idx on public.follows (author_id);
create index if not exists messages_recipient_id_fk_idx on public.messages (recipient_id);
create index if not exists ratings_user_id_fk_idx on public.ratings (user_id);
create index if not exists reading_circle_posts_author_id_fk_idx on public.reading_circle_posts (author_id);
create index if not exists reading_circle_posts_parent_id_fk_idx on public.reading_circle_posts (parent_id);
create index if not exists reading_circles_book_id_fk_idx on public.reading_circles (book_id);
create index if not exists reading_circles_owner_id_fk_idx on public.reading_circles (owner_id);
create index if not exists reading_progress_book_id_fk_idx on public.reading_progress (book_id);
create index if not exists reports_reporter_id_fk_idx on public.reports (reporter_id);
create index if not exists series_author_id_fk_idx on public.series (author_id);
create index if not exists timeline_events_book_id_fk_idx on public.timeline_events (book_id);
create index if not exists writing_challenge_entries_author_id_fk_idx on public.writing_challenge_entries (author_id);
create index if not exists writing_challenge_entries_book_id_fk_idx on public.writing_challenge_entries (book_id);
create index if not exists writing_challenge_votes_entry_fk_idx on public.writing_challenge_votes (challenge_id, book_id);
create index if not exists writing_challenge_votes_voter_id_fk_idx on public.writing_challenge_votes (voter_id);
create index if not exists writing_challenges_created_by_fk_idx on public.writing_challenges (created_by);

-- Azonos definíciójú indexek: egy marad (books_published_idx, chapters_book_order_idx).
drop index if exists public.books_published_updated_idx;
drop index if exists public.books_status_updated_idx;
drop index if exists public.chapters_book_idx;

-- =====================================================================================
-- 4) Időzített publikálás a szerveren
-- =====================================================================================
-- Eddig csak akkor publikált, ha a szerző appja a megadott napon vagy utána szinkronizált.
-- Mostantól a szerver 10 percenként ellenőrzi (magyar idő szerint), és a követők értesítést kapnak.

create extension if not exists pg_cron with schema pg_catalog;

-- A rendszer által (pl. időzítésre) létrehozott értesítésekhez is menjen push. Ilyenkor nincs
-- felhasználói token: a Vaultban tárolt közös titokkal azonosítja magát a send-community-push függvény
-- felé (Edge Function titok: REGELO_SYSTEM_PUSH_SECRET; Vault: regelo_system_push_secret).
create or replace function public.regelo_dispatch_push()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    request_headers json;
    api_key text;
    user_token text;
    system_secret text;
    extra_headers jsonb;
begin
    if new.actor_id is null then
        return null;
    end if;
    begin
        request_headers := nullif(current_setting('request.headers', true), '')::json;
        api_key := coalesce(
            nullif(request_headers ->> 'apikey', ''),
            'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBxbWZlemt2YXNscWVkYWx3d25uIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk4NzQ5MzIsImV4cCI6MjEwNTQ1MDkzMn0.8Cp9stMWB3TpSUw3ph9Mev8Er8N7odmfrIDy9jra-cU'
        );
        user_token := regexp_replace(coalesce(request_headers ->> 'authorization', ''), '^Bearer\s+', '', 'i');
        if user_token <> '' and user_token <> api_key then
            extra_headers := jsonb_build_object('x-regelo-user-token', user_token);
        elsif coalesce(current_setting('regelo.system_push', true), '') = '1' then
            select s.decrypted_secret into system_secret
              from vault.decrypted_secrets s
             where s.name = 'regelo_system_push_secret'
             limit 1;
            if system_secret is null then
                raise log 'regelo push: hiányzik a regelo_system_push_secret (%)', new.id;
                return null;
            end if;
            extra_headers := jsonb_build_object('x-regelo-system-secret', system_secret);
        else
            raise log 'regelo push: nincs felhasználói token az értesítéshez (%)', new.id;
            return null;
        end if;
        perform net.http_post(
            url := 'https://pqmfezkvaslqedalwwnn.supabase.co/functions/v1/send-community-push',
            body := jsonb_build_object('notificationId', new.id),
            headers := jsonb_build_object(
                'Content-Type', 'application/json',
                'apikey', api_key,
                'Authorization', 'Bearer ' || api_key
            ) || extra_headers,
            timeout_milliseconds := 5000
        );
    exception when others then
        raise warning 'regelo push dispatch failed: %', sqlerrm;
    end;
    return null;
end;
$$;
revoke execute on function public.regelo_dispatch_push() from public, anon, authenticated;

create or replace function public.regelo_publish_scheduled()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer := 0;
  r record;
begin
  perform set_config('regelo.system_push', '1', true);
  for r in
    select b.id
      from public.books b
     where b.publish_status = 'SCHEDULED'
       and b.scheduled_date is not null
       and b.scheduled_date <= (now() at time zone 'Europe/Budapest')::date
     for update skip locked
  loop
    -- Ha minden fejezet piszkozat, a megjelenéskor ezek is publikussá válnak (különben üres lenne a könyv),
    -- ugyanúgy, ahogy az appban. Az updated_at frissítése miatt a szerző telefonja is átveszi.
    if not exists (select 1 from public.chapters c where c.book_id = r.id and c.is_draft = false) then
      update public.chapters set is_draft = false, updated_at = now() where book_id = r.id;
    end if;
    update public.books set publish_status = 'PUBLISHED' where id = r.id;
    v_count := v_count + 1;
  end loop;
  perform set_config('regelo.system_push', '', true);
  return v_count;
end;
$$;
revoke execute on function public.regelo_publish_scheduled() from public, anon, authenticated;

select cron.schedule('regelo-publish-scheduled', '*/10 * * * *', 'select public.regelo_publish_scheduled()');

-- =====================================================================================
-- 5) Átfedő (több megengedő) szabályok összevonása (Performance Advisor)
-- =====================================================================================
-- Az ALL típusú írószabályok a SELECT-et is lefedték, így olvasáskor két szabály futott.
-- Ugyanaz a jogosultság, műveletenként szétbontva / VAGY-gyal összevonva.

-- books
drop policy if exists books_write on public.books;
drop policy if exists books_insert on public.books;
drop policy if exists books_update on public.books;
drop policy if exists books_delete on public.books;
create policy books_insert on public.books for insert to authenticated with check ((select auth.uid()) = author_id);
create policy books_update on public.books for update to authenticated using ((select auth.uid()) = author_id) with check ((select auth.uid()) = author_id);
create policy books_delete on public.books for delete to authenticated using ((select auth.uid()) = author_id);

-- chapters
drop policy if exists chapters_write on public.chapters;
drop policy if exists chapters_insert on public.chapters;
drop policy if exists chapters_update on public.chapters;
drop policy if exists chapters_delete on public.chapters;
create policy chapters_insert on public.chapters for insert to authenticated
  with check (exists (select 1 from public.books b where b.id = chapters.book_id and b.author_id = (select auth.uid())));
create policy chapters_update on public.chapters for update to authenticated
  using (exists (select 1 from public.books b where b.id = chapters.book_id and b.author_id = (select auth.uid())))
  with check (exists (select 1 from public.books b where b.id = chapters.book_id and b.author_id = (select auth.uid())));
create policy chapters_delete on public.chapters for delete to authenticated
  using (exists (select 1 from public.books b where b.id = chapters.book_id and b.author_id = (select auth.uid())));

-- follows (régi tábla)
drop policy if exists follows_write on public.follows;
drop policy if exists follows_insert on public.follows;
drop policy if exists follows_update on public.follows;
drop policy if exists follows_delete on public.follows;
create policy follows_insert on public.follows for insert to authenticated with check ((select auth.uid()) = follower_id);
create policy follows_update on public.follows for update to authenticated using ((select auth.uid()) = follower_id) with check ((select auth.uid()) = follower_id);
create policy follows_delete on public.follows for delete to authenticated using ((select auth.uid()) = follower_id);

-- ratings (régi tábla)
drop policy if exists ratings_write on public.ratings;
drop policy if exists ratings_insert on public.ratings;
drop policy if exists ratings_update on public.ratings;
drop policy if exists ratings_delete on public.ratings;
create policy ratings_insert on public.ratings for insert to authenticated with check ((select auth.uid()) = user_id);
create policy ratings_update on public.ratings for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy ratings_delete on public.ratings for delete to authenticated using ((select auth.uid()) = user_id);

-- series
drop policy if exists series_write on public.series;
drop policy if exists series_insert on public.series;
drop policy if exists series_update on public.series;
drop policy if exists series_delete on public.series;
create policy series_insert on public.series for insert to authenticated with check ((select auth.uid()) = author_id);
create policy series_update on public.series for update to authenticated using ((select auth.uid()) = author_id) with check ((select auth.uid()) = author_id);
create policy series_delete on public.series for delete to authenticated using ((select auth.uid()) = author_id);

-- public_profiles
drop policy if exists public_profiles_write_own on public.public_profiles;
drop policy if exists public_profiles_insert_own on public.public_profiles;
drop policy if exists public_profiles_update_own on public.public_profiles;
drop policy if exists public_profiles_delete_own on public.public_profiles;
create policy public_profiles_insert_own on public.public_profiles for insert to authenticated with check ((select auth.uid()) = user_id);
create policy public_profiles_update_own on public.public_profiles for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy public_profiles_delete_own on public.public_profiles for delete to authenticated using ((select auth.uid()) = user_id);

-- reader_library_items: saját elemek + nyilvános olvasási listák
drop policy if exists "Users manage their own library items" on public.reader_library_items;
drop policy if exists "Public reading lists are visible" on public.reader_library_items;
drop policy if exists reader_library_items_read on public.reader_library_items;
drop policy if exists reader_library_items_insert on public.reader_library_items;
drop policy if exists reader_library_items_update on public.reader_library_items;
drop policy if exists reader_library_items_delete on public.reader_library_items;
create policy reader_library_items_read on public.reader_library_items for select to authenticated
  using ((select auth.uid()) = user_id
         or (kind = 'COLLECTION' and is_deleted = false and coalesce((payload ->> 'public')::boolean, false)));
create policy reader_library_items_insert on public.reader_library_items for insert to authenticated with check ((select auth.uid()) = user_id);
create policy reader_library_items_update on public.reader_library_items for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy reader_library_items_delete on public.reader_library_items for delete to authenticated using ((select auth.uid()) = user_id);

-- reports: saját jelentés vagy moderátor
drop policy if exists "Moderators can view all reports" on public.reports;
drop policy if exists reports_select on public.reports;
drop policy if exists reports_read on public.reports;
create policy reports_read on public.reports for select to authenticated
  using ((select auth.uid()) = reporter_id or public.regelo_is_moderator());

-- community_reports: saját jelentés vagy moderátor
drop policy if exists reports_read_own on public.community_reports;
drop policy if exists reports_read_moderators on public.community_reports;
drop policy if exists community_reports_read on public.community_reports;
create policy community_reports_read on public.community_reports for select to authenticated
  using ((select auth.uid()) = reporter_id
         or exists (select 1 from public.community_moderators m where m.user_id = (select auth.uid())));

-- chapter_comments: olvasás és módosítás (szerző vagy moderátor)
drop policy if exists chapter_comments_read on public.chapter_comments;
drop policy if exists chapter_comments_read_moderators on public.chapter_comments;
create policy chapter_comments_read on public.chapter_comments for select to authenticated
  using ((((deleted_at is null) or ((select auth.uid()) = author_id)) and not public.regelo_i_blocked(author_id))
         or public.regelo_is_moderator());
drop policy if exists chapter_comments_moderate on public.chapter_comments;
drop policy if exists chapter_comments_update on public.chapter_comments;
create policy chapter_comments_update on public.chapter_comments for update to authenticated
  using ((select auth.uid()) = author_id or public.regelo_is_moderator())
  with check ((select auth.uid()) = author_id or public.regelo_is_moderator());
