-- Regélő app v1.1 – írói statisztika (fejezetenkénti olvasói „tölcsér”, napi megtekintések, kedvelések, kommentek).
-- Idempotens. Csak a könyv szerzője kérdezheti le; az olvasók személye nem derül ki, csak összesített számok.

create or replace function public.regelo_book_stats(p_book_id uuid, p_days int default 30)
returns jsonb
language plpgsql stable security definer
set search_path = public
as $$
declare
  v_days int := least(greatest(coalesce(p_days, 30), 7), 90);
  v_stall_ms bigint := (extract(epoch from now() - interval '14 days') * 1000)::bigint;
  v_published int;
  result jsonb;
begin
  if not exists (select 1 from books where id = p_book_id and author_id = auth.uid()) then
    raise exception 'Csak a saját történeted statisztikáját nézheted meg.' using errcode = '42501';
  end if;

  select count(*) into v_published from chapters where book_id = p_book_id and is_draft = false;

  with prog as (
    select chapter_index, percent, updated_at
    from reader_progress
    where book_id = p_book_id and is_deleted = false and user_id <> auth.uid()
  ),
  idx as (
    select generate_series(0, greatest(coalesce((select max(chapter_index) from prog), 0), v_published - 1, 0)) as i
  ),
  readers as (
    select coalesce(jsonb_agg(jsonb_build_object(
      'chapter_index', i,
      'current', (select count(*) from prog where chapter_index = i),
      'reached', (select count(*) from prog where chapter_index >= i),
      'stalled', (select count(*) from prog where chapter_index = i and percent < 100 and updated_at < v_stall_ms)
    ) order by i), '[]'::jsonb) as j
    from idx
  ),
  days as (
    select coalesce(jsonb_agg(jsonb_build_object(
      'day', d::date,
      'views', (select count(*) from book_views v where v.book_id = p_book_id and v.viewed_on = d::date)
    ) order by d), '[]'::jsonb) as j
    from generate_series(current_date - (v_days - 1), current_date, interval '1 day') d
  ),
  engagement as (
    select coalesce(jsonb_agg(jsonb_build_object(
      'chapter_id', c.id,
      'likes', (select count(*) from chapter_likes l where l.chapter_id = c.id),
      'comments', (select count(*) from chapter_comments k where k.chapter_id = c.id and k.deleted_at is null)
    )), '[]'::jsonb) as j
    from chapters c where c.book_id = p_book_id
  )
  select jsonb_build_object(
    'readers', (select j from readers),
    'views_daily', (select j from days),
    'engagement', (select j from engagement),
    'total_readers', (select count(*) from prog),
    'finished_readers', (select count(*) from prog where percent >= 100),
    'views_total', (select count(*) from book_views where book_id = p_book_id)
  ) into result;
  return result;
end;
$$;

revoke all on function public.regelo_book_stats(uuid, int) from public;
grant execute on function public.regelo_book_stats(uuid, int) to authenticated;

select 'ok' as out;
