-- Regélő app v1.0.1 – felhasználókeresés és hivatalos Regélő-fiók. Idempotens: többször is futtatható.

-- 1) Ékezetfüggetlen keresés ("regelo" = "Regélő").
create extension if not exists unaccent with schema extensions;

-- 2) Hivatalos fiókok (jelvény az appban, hírei mindenkinek megjelennek). Csak SQL-ből írható.
create table if not exists public.regelo_official_accounts (
  user_id uuid primary key references auth.users(id) on delete cascade,
  label text not null default 'Hivatalos',
  created_at timestamptz not null default now()
);
alter table public.regelo_official_accounts enable row level security;
drop policy if exists official_read on public.regelo_official_accounts;
create policy official_read on public.regelo_official_accounts for select to anon, authenticated using (true);
grant select on public.regelo_official_accounts to anon, authenticated;

insert into public.regelo_official_accounts (user_id, label)
select id, 'Hivatalos' from auth.users where email = 'regeloapp@gmail.com'
on conflict (user_id) do nothing;

-- A hivatalos fiók moderátor is (jelentések kezelése az appban: Én › Moderáció).
insert into public.community_moderators (user_id)
select id from auth.users where email = 'regeloapp@gmail.com'
on conflict (user_id) do nothing;

-- 3) Felhasználókeresés: név vagy @felhasználónév, ékezet nélkül is; a pontos/eleje-egyezés és a hivatalos fiók előre kerül.
create or replace function public.regelo_search_profiles(q text)
returns setof public.public_profiles
language sql stable security invoker
set search_path = public, extensions
as $$
  with s as (
    select replace(replace(replace(
      lower(extensions.unaccent(btrim(regexp_replace(coalesce(q, ''), '^@+', '')))),
      '\', '\\'), '%', '\%'), '_', '\_') as t
  )
  select p.*
  from public.public_profiles p, s
  where char_length(s.t) >= 2
    and (lower(extensions.unaccent(p.display_name)) like '%' || s.t || '%'
         or lower(p.username) like '%' || s.t || '%')
  order by
    (lower(p.username) = s.t) desc,
    (lower(p.username) like s.t || '%' or lower(extensions.unaccent(p.display_name)) like s.t || '%') desc,
    exists (select 1 from public.regelo_official_accounts o where o.user_id = p.user_id) desc,
    p.display_name
  limit 40;
$$;
revoke all on function public.regelo_search_profiles(text) from public;
grant execute on function public.regelo_search_profiles(text) to authenticated;

-- 4) A hivatalos fiók hírei minden felhasználónak értesítést (és pusht) küldenek, nem csak a követőknek.
create or replace function public.regelo_notify_announcement() returns trigger
language plpgsql security definer set search_path to 'public' as $function$
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

select json_build_object(
  'official', (select json_agg(user_id) from public.regelo_official_accounts),
  'moderators', (select json_agg(user_id) from public.community_moderators),
  'search_test', (select json_agg(username) from public.regelo_search_profiles('regelo'))
)::text as out;
