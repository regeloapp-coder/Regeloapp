-- Regélő 1.3 – jogosultsági tesztsor (hivatalos kiadványok, szerepkörök, rate limit, szövegtisztítás, export).
-- Tranzakcióban fut, több felhasználó nevében; a végén kivétellel visszagörgeti az összes változást,
-- és a hibaüzenetben kiírja az eredménysort. Minden sor: OK / HIBA + leírás.
-- Futtatás: Supabase SQL-szerkesztő (vagy Management API), egyben.
begin;
create temp table t_log(n serial, ok boolean, msg text) on commit drop;
grant all on t_log to authenticated, anon;
grant usage on sequence t_log_n_seq to authenticated, anon;

do $$
declare a uuid; b uuid; o uuid;
begin
  select user_id into o from public.regelo_official_accounts limit 1;
  select user_id into a from public.public_profiles where user_id <> o order by updated_at limit 1;
  select user_id into b from public.public_profiles where user_id not in (o, a) order by updated_at desc limit 1;
  perform set_config('t.a', a::text, false);
  perform set_config('t.b', b::text, false);
  perform set_config('t.o', o::text, false);
  perform set_config('t.kavehaz', (select book_id::text from public.regelo_official_books where slug = 'kavehaz'), false);
  perform set_config('t.kchapter', (select c.id::text from public.chapters c
                                     where c.book_id = (select book_id from public.regelo_official_books where slug = 'kavehaz')
                                       and c.is_draft = false order by c.order_index desc limit 1), false);
end $$;

insert into t_log(ok, msg)
values (
  not has_function_privilege('authenticated', 'public.delete_own_account()', 'EXECUTE')
  and not has_function_privilege('anon', 'public.delete_own_account()', 'EXECUTE'),
  'A fiók törlése csak a Storage-t is takarító Edge Functionön keresztül érhető el'
);

-- ================= A (hétköznapi felhasználó) =================
select set_config('request.jwt.claims', json_build_object('sub', current_setting('t.a'), 'role', 'authenticated')::text, true);
set local role authenticated;
do $$
declare
  a uuid := current_setting('t.a')::uuid; b uuid := current_setting('t.b')::uuid;
  kb uuid := current_setting('t.kavehaz')::uuid; kc uuid := current_setting('t.kchapter')::uuid;
  n int; ok boolean; v text; j jsonb; i int;
begin
  select count(*) into n from public.regelo_official_books;
  insert into t_log(ok, msg) values (n = 4, 'A látja a 4 hivatalos könyvet: ' || n);

  begin
    insert into public.regelo_official_books(book_id, slug) values (gen_random_uuid(), 'hirek');
    insert into t_log(ok, msg) values (false, 'A hivatalos könyvet vehetett fel (TILOS)');
  exception when others then insert into t_log(ok, msg) values (true, 'A nem vehet fel hivatalos könyvet: ' || sqlstate);
  end;

  select count(*) into n from public.regelo_official_chapter_sources;
  insert into t_log(ok, msg) values (n = 0, 'A nem látja a forrástáblát: ' || n);

  insert into public.regelo_official_subscriptions(user_id, book_id) values (a, kb) on conflict do nothing;
  select subscribed into ok from public.regelo_official_overview() where book_id = kb;
  insert into t_log(ok, msg) values (ok, 'A feliratkozott, az áttekintés mutatja: ' || ok);

  begin
    insert into public.regelo_official_subscriptions(user_id, book_id) values (b, kb);
    insert into t_log(ok, msg) values (false, 'A más nevében feliratkozhatott (TILOS)');
  exception when others then insert into t_log(ok, msg) values (true, 'A nem iratkozhat fel más nevében: ' || sqlstate);
  end;

  select count(*) into n from public.regelo_roles;
  insert into t_log(ok, msg) values (n = 0, 'A nem lát szerepkört: ' || n);

  begin
    perform public.regelo_official_append('kavehaz', 'DISCUSSION', 'teszt', 'x', 'y');
    insert into t_log(ok, msg) values (false, 'A hívhatta a hozzáfűzést (TILOS)');
  exception when others then insert into t_log(ok, msg) values (true, 'A nem hívhatja a hozzáfűzést: ' || sqlstate);
  end;

  begin
    perform public.regelo_rate_limit('x', 1, interval '1 minute');
    insert into t_log(ok, msg) values (false, 'A hívhatta a rate limit függvényt (TILOS)');
  exception when others then insert into t_log(ok, msg) values (true, 'A nem hívhatja közvetlenül a rate limitet: ' || sqlstate);
  end;

  begin
    insert into public.book_ratings(user_id, book_id, stars, review, updated_at) values (a, kb, 5, '', 1);
    insert into t_log(ok, msg) values (false, 'A értékelhette a hivatalos könyvet (TILOS)');
  exception when others then insert into t_log(ok, msg) values (sqlstate = '23514', 'A nem értékelheti a hivatalos könyvet: ' || sqlstate);
  end;

  update public.chapters set title = 'hack' where id = kc;
  get diagnostics n = row_count;
  insert into t_log(ok, msg) values (n = 0, 'A nem szerkesztheti a hivatalos részt: ' || n);

  -- Szövegtisztítás: irányváltó karakter (U+202E) ki, ZWJ (U+200D, emoji-sorozat) marad.
  insert into public.chapter_comments(id, chapter_id, author_id, paragraph_index, body)
  values (gen_random_uuid(), kc, a, 0, 'Szia' || chr(8238) || ' világ ' || chr(128105) || chr(8205) || chr(128187))
  returning body into v;
  insert into t_log(ok, msg) values (position(chr(8238) in v) = 0 and position(chr(8205) in v) > 0,
                                     'Tisztítás: U+202E törölve, ZWJ megmaradt');

  -- Rate limit: percenként legfeljebb 10 komment (az előző 1 + 9 = 10 még megy, a 11. nem).
  for i in 1..9 loop
    insert into public.chapter_comments(id, chapter_id, author_id, paragraph_index, body) values (gen_random_uuid(), kc, a, 0, 'k' || i);
  end loop;
  begin
    insert into public.chapter_comments(id, chapter_id, author_id, paragraph_index, body) values (gen_random_uuid(), kc, a, 0, 'k11');
    insert into t_log(ok, msg) values (false, 'A 11. komment is átment (HIBA)');
  exception when others then insert into t_log(ok, msg) values (sqlstate = 'P0429', 'A 11. komment egy percen belül tiltva: ' || sqlstate);
  end;

  j := public.regelo_export_my_data();
  insert into t_log(ok, msg) values ((j -> 'profile' ->> 'user_id') = a::text
      and not exists (select 1 from jsonb_array_elements(j -> 'comments') c where c ->> 'author_id' <> a::text),
      'Export: csak a saját adatok, ' || jsonb_array_length(j -> 'comments') || ' komment');
  insert into t_log(ok, msg) values (
      jsonb_array_length(j -> 'community_reports_filed') =
        (select count(*) from public.community_reports where reporter_id = a)
      and not exists (select 1 from jsonb_array_elements(j -> 'community_reports_filed') r where r ->> 'reporter_id' <> a::text),
      'Export: saját közösségi jelentések, teljes darabszám és nincs más felhasználó adata');
  insert into t_log(ok, msg) values (
      jsonb_array_length(j -> 'community_notifications') =
        (select count(*) from public.community_notifications where recipient_id = a)
      and not exists (select 1 from jsonb_array_elements(j -> 'community_notifications') n where n ->> 'recipient_id' <> a::text),
      'Export: saját értesítések, teljes darabszám és nincs más felhasználó adata');

  begin
    j := public.regelo_export_my_data();
    insert into t_log(ok, msg) values (false, 'Az export óránként többször is ment (HIBA)');
  exception when others then insert into t_log(ok, msg) values (sqlstate = 'P0429', 'Export óránként egyszer: ' || sqlstate);
  end;
end $$;
reset role;

-- ================= B =================
select set_config('request.jwt.claims', json_build_object('sub', current_setting('t.b'), 'role', 'authenticated')::text, true);
set local role authenticated;
do $$
declare n int;
begin
  select count(*) into n from public.regelo_official_subscriptions where user_id = current_setting('t.a')::uuid;
  insert into t_log(ok, msg) values (n = 0, 'B nem látja A feliratkozását: ' || n);
end $$;
reset role;

-- ================= Hivatalos fiók =================
select set_config('request.jwt.claims', json_build_object('sub', current_setting('t.o'), 'role', 'authenticated')::text, true);
set local role authenticated;
do $$
declare n int; done boolean;
begin
  select count(*) into n from public.regelo_roles;
  insert into t_log(ok, msg) values (n >= 1, 'A hivatalos fiók látja a saját szerepkörét: ' || n);
  update public.books set is_completed = true where id = current_setting('t.kavehaz')::uuid returning is_completed into done;
  insert into t_log(ok, msg) values (done = false, 'Hivatalos könyv nem lehet befejezett: ' || coalesce(done::text, 'null'));
end $$;
reset role;

-- ================= anon =================
set local role anon;
do $$
declare n int;
begin
  select count(*) into n from public.regelo_app_config;
  insert into t_log(ok, msg) values (n >= 1, 'anon olvashatja az app-beállításokat: ' || n);
  begin
    perform public.regelo_export_my_data();
    insert into t_log(ok, msg) values (false, 'anon exportálhatott (TILOS)');
  exception when others then insert into t_log(ok, msg) values (true, 'anon nem exportálhat: ' || sqlstate);
  end;
  begin
    perform * from public.regelo_official_overview();
    insert into t_log(ok, msg) values (false, 'anon hívhatta az áttekintést (TILOS)');
  exception when others then insert into t_log(ok, msg) values (true, 'anon nem hívhatja az áttekintést: ' || sqlstate);
  end;
end $$;
reset role;

do $$ begin
  raise exception 'EREDMÉNY (visszagörgetve): %',
    (select string_agg(case when ok then 'OK   ' else 'HIBA ' end || msg, ' | ' order by n) from t_log);
end $$;
