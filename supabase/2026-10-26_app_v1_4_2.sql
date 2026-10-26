-- Regélő app v1.4.2 – egészítsük ki a fiók adatexportját a közösségi jelentésekkel és értesítésekkel.
create or replace function public.regelo_export_my_data()
returns jsonb
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'Bejelentkezés szükséges.' using errcode = '28000';
  end if;
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
    'community_reports_filed', coalesce((select jsonb_agg(jsonb_build_object(
                                'id', r.id, 'reporter_id', r.reporter_id, 'comment_id', r.comment_id,
                                'reported_user_id', r.reported_user_id, 'reason', r.reason,
                                'status', r.status, 'created_at', r.created_at) order by r.created_at)
                                from public.community_reports r where r.reporter_id = v_uid), '[]'),
    'community_notifications', coalesce((select jsonb_agg(jsonb_build_object(
                                'id', n.id, 'recipient_id', n.recipient_id, 'actor_id', n.actor_id,
                                'kind', n.kind, 'book_id', n.book_id, 'comment_id', n.comment_id,
                                'message_id', n.message_id, 'announcement_id', n.announcement_id,
                                'payload', n.payload, 'read_at', n.read_at,
                                'created_at', n.created_at) order by n.created_at)
                                from public.community_notifications n where n.recipient_id = v_uid), '[]'),
    'official_subscriptions', coalesce((select jsonb_agg(to_jsonb(s)) from public.regelo_official_subscriptions s where s.user_id = v_uid), '[]'),
    'push_devices', (select count(*) from public.device_push_tokens t where t.user_id = v_uid)
  );
end
$function$;
