-- Regélő app v1.4 – atomikus olvasásbefejezés és versenyhelyzet-biztos rate limit.
-- Előbb futtasd az éles Supabase adatbázison, majd csak ezután add ki az ezt használó appot.

-- A számlálás és az esemény beszúrása egyazon felhasználó/művelet párra sorosítva történjen,
-- hogy párhuzamos kérések ne tudják megkerülni a limitet.
create or replace function public.regelo_rate_limit(p_action text, p_max integer, p_window interval)
returns void
language plpgsql
security definer
set search_path = public
as $function$
declare
  v_uid uuid := auth.uid();
  v_n int;
begin
  if v_uid is null then return; end if;
  if p_action is null or char_length(p_action) not between 1 and 64
     or p_max is null or p_max < 1 or p_window is null or p_window <= interval '0' then
    raise exception 'Invalid rate-limit configuration.' using errcode = '22023';
  end if;
  if exists (select 1 from public.regelo_official_accounts o where o.user_id = v_uid) then return; end if;

  perform pg_advisory_xact_lock(hashtextextended(v_uid::text || ':' || p_action, 0));
  select count(*) into v_n
    from public.regelo_rate_events
   where action = p_action and user_id = v_uid and at > now() - p_window;
  if v_n >= p_max then
    raise exception 'Túl sok művelet rövid idő alatt. Várj egy kicsit, és próbáld újra.' using errcode = 'P0429';
  end if;
  insert into public.regelo_rate_events (user_id, action) values (v_uid, p_action);
end
$function$;

-- A befejezés szerveridőt és egyedi (felhasználó, könyv, év) kulcsot használ.
-- Az ismételt hívás false-t ad, a korábban eltávolított bejegyzés biztonságosan visszaállítható.
create or replace function public.regelo_mark_book_finished(p_book_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $function$
declare
  v_uid uuid := auth.uid();
  v_title text;
  v_finished_at timestamptz := now();
  v_year integer;
  v_finished_ms bigint;
  v_inserted boolean := false;
begin
  if v_uid is null then
    raise exception 'Bejelentkezés szükséges.' using errcode = '28000';
  end if;

  select b.title into v_title
    from public.books b
   where b.id = p_book_id
     and b.publish_status = 'PUBLISHED'
     and b.is_completed = true;
  if not found then
    raise exception 'A könyv nem található vagy még nincs befejezve.' using errcode = '22023';
  end if;

  v_year := extract(year from v_finished_at at time zone 'Europe/Budapest')::integer;
  v_finished_ms := floor(extract(epoch from v_finished_at) * 1000)::bigint;

  insert into public.reader_library_items as existing
    (user_id, kind, item_id, payload, is_deleted, updated_at)
  values (
    v_uid,
    'FINISHED',
    v_year::text || ':' || p_book_id::text,
    jsonb_build_object(
      'book_id', p_book_id::text,
      'title', left(v_title, 200),
      'finished_at', v_finished_ms,
      'year', v_year
    ),
    false,
    v_finished_ms
  )
  on conflict (user_id, kind, item_id) do update
    set payload = excluded.payload,
        is_deleted = false,
        updated_at = excluded.updated_at
    where existing.is_deleted = true
  returning true into v_inserted;

  return coalesce(v_inserted, false);
end
$function$;

revoke all on function public.regelo_mark_book_finished(uuid) from public, anon;
grant execute on function public.regelo_mark_book_finished(uuid) to authenticated;
