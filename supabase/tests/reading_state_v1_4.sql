-- v1.4 olvasásbefejezés RPC: idempotens, saját fiókhoz kötött, csak befejezett publikált könyvre.
-- Futtatás Supabase SQL Editorban postgres szerepkörrel; a tranzakció a tesztadatokat visszagörgeti.
begin;

create temp table t_result(ok boolean not null, message text not null) on commit drop;
grant all on t_result to authenticated;

do $$
declare
  v_user uuid;
  v_book uuid := gen_random_uuid();
  v_open_book uuid := gen_random_uuid();
begin
  select p.id into v_user
    from public.profiles p
   where not exists (
     select 1 from public.regelo_official_accounts o where o.user_id = p.id
   )
   limit 1;
  if v_user is null then
    raise exception 'A teszthez legalább egy nem hivatalos profil szükséges.';
  end if;

  insert into public.books (id, author_id, title, is_completed, publish_status)
  values (v_book, v_user, 'v1.4 test complete book', true, 'PUBLISHED');
  insert into public.books (id, author_id, title, is_completed, publish_status)
  values (v_open_book, v_user, 'v1.4 test ongoing book', false, 'PUBLISHED');
  perform set_config('t.v1_4_user', v_user::text, true);
  perform set_config('t.v1_4_book', v_book::text, true);
  perform set_config('t.v1_4_open_book', v_open_book::text, true);
end $$;

select set_config(
  'request.jwt.claims',
  json_build_object('sub', current_setting('t.v1_4_user'), 'role', 'authenticated')::text,
  true
);
set local role authenticated;

do $$
declare
  v_first boolean;
  v_second boolean;
  v_restored boolean;
  v_count integer;
  v_key text;
begin
  v_first := public.regelo_mark_book_finished(current_setting('t.v1_4_book')::uuid);
  v_second := public.regelo_mark_book_finished(current_setting('t.v1_4_book')::uuid);
  v_key := to_char(now() at time zone 'Europe/Budapest', 'YYYY') || ':' || current_setting('t.v1_4_book');
  select count(*) into v_count
    from public.reader_library_items
   where user_id = (select auth.uid()) and kind = 'FINISHED' and item_id = v_key and not is_deleted;
  insert into t_result
  values (v_first and not v_second and v_count = 1,
          'First completion inserts once; duplicate call is a no-op; active records=' || v_count);

  update public.reader_library_items
     set is_deleted = true
   where user_id = (select auth.uid()) and kind = 'FINISHED' and item_id = v_key;
  v_restored := public.regelo_mark_book_finished(current_setting('t.v1_4_book')::uuid);
  select count(*) into v_count
    from public.reader_library_items
   where user_id = (select auth.uid()) and kind = 'FINISHED' and item_id = v_key and not is_deleted;
  insert into t_result
  values (v_restored and v_count = 1, 'Removing and rereading restores one active completion');

  begin
    perform public.regelo_mark_book_finished(current_setting('t.v1_4_open_book')::uuid);
    insert into t_result values (false, 'An unfinished book was accepted');
  exception when sqlstate '22023' then
    insert into t_result values (true, 'An unfinished book was rejected');
  end;
end $$;

reset role;
select * from t_result;
rollback;
