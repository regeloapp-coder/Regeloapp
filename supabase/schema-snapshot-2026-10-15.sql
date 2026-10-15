-- Regélő – teljes adatbázis-pillanatkép (public séma + storage szabályok + cron), 2026-10-15T11:06:21.288Z
-- Csak dokumentáció és visszaállítási segédlet: a migrációk a dátumozott fájlokban vannak.

-- ===================== TÁBLÁK =====================
create table if not exists public.app_error_reports (
  id uuid not null default gen_random_uuid(),
  user_id uuid,
  app_version text not null default ''::text,
  device text not null default ''::text,
  context text not null default ''::text,
  message text not null default ''::text,
  stack text not null default ''::text,
  created_at timestamp with time zone not null default now(),
  constraint app_error_reports_pkey PRIMARY KEY (id),
  constraint app_error_reports_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);
alter table public.app_error_reports enable row level security;

create table if not exists public.author_announcements (
  id uuid not null default gen_random_uuid(),
  author_id uuid not null,
  title text not null,
  body text not null,
  book_id uuid,
  created_at timestamp with time zone not null default now(),
  constraint author_announcements_pkey PRIMARY KEY (id),
  constraint author_announcements_author_id_fkey FOREIGN KEY (author_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint author_announcements_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE SET NULL,
  constraint author_announcements_body_check CHECK (((char_length(TRIM(BOTH FROM body)) >= 1) AND (char_length(TRIM(BOTH FROM body)) <= 5000))),
  constraint author_announcements_title_check CHECK (((char_length(TRIM(BOTH FROM title)) >= 1) AND (char_length(TRIM(BOTH FROM title)) <= 160)))
);
alter table public.author_announcements enable row level security;

create table if not exists public.blocked_users (
  user_id uuid not null,
  blocked_id uuid not null,
  created_at timestamp with time zone not null default now(),
  constraint blocked_users_pkey PRIMARY KEY (user_id, blocked_id),
  constraint blocked_users_blocked_id_fkey FOREIGN KEY (blocked_id) REFERENCES profiles(id) ON DELETE CASCADE,
  constraint blocked_users_user_id_fkey FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE,
  constraint blocked_users_not_self CHECK ((user_id <> blocked_id))
);
alter table public.blocked_users enable row level security;

create table if not exists public.book_likes (
  user_id uuid not null,
  book_id uuid not null,
  created_at timestamp with time zone not null default now(),
  constraint book_likes_pkey PRIMARY KEY (user_id, book_id),
  constraint book_likes_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  constraint book_likes_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);
alter table public.book_likes enable row level security;

create table if not exists public.book_ratings (
  user_id uuid not null,
  book_id uuid not null,
  stars smallint not null,
  review text not null default ''::text,
  updated_at bigint not null,
  created_at timestamp with time zone not null default now(),
  constraint book_ratings_pkey PRIMARY KEY (user_id, book_id),
  constraint book_ratings_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  constraint book_ratings_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint book_ratings_review_check CHECK ((char_length(review) <= 2000)),
  constraint book_ratings_stars_check CHECK (((stars >= 1) AND (stars <= 5))),
  constraint book_ratings_updated_at_check CHECK ((updated_at >= 0))
);
alter table public.book_ratings enable row level security;

create table if not exists public.book_views (
  user_id uuid not null,
  book_id uuid not null,
  viewed_on date not null default CURRENT_DATE,
  constraint book_views_pkey PRIMARY KEY (user_id, book_id, viewed_on),
  constraint book_views_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  constraint book_views_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);
alter table public.book_views enable row level security;

create table if not exists public.books (
  id uuid not null default gen_random_uuid(),
  author_id uuid not null,
  title text not null,
  pen_name text not null default ''::text,
  genre text not null default ''::text,
  short_description text not null default ''::text,
  cover_color_hex bigint not null default 2843733,
  cover_url text not null default ''::text,
  is_completed boolean not null default false,
  age_rating text not null default 'NONE'::text,
  content_warnings text[] not null default '{}'::text[],
  theme_tags text[] not null default '{}'::text[],
  publish_status text not null default 'DRAFT'::text,
  scheduled_date date,
  series_id uuid,
  volume_number integer not null default 0,
  view_count integer not null default 0,
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  published_at timestamp with time zone,
  constraint books_pkey PRIMARY KEY (id),
  constraint books_author_id_fkey FOREIGN KEY (author_id) REFERENCES profiles(id) ON DELETE CASCADE,
  constraint books_series_id_fkey FOREIGN KEY (series_id) REFERENCES series(id) ON DELETE SET NULL
);
alter table public.books enable row level security;

create table if not exists public.chapter_comments (
  id uuid not null default gen_random_uuid(),
  chapter_id uuid not null,
  author_id uuid not null,
  parent_id uuid,
  paragraph_index integer not null default 0,
  body text not null,
  deleted_at timestamp with time zone,
  created_at timestamp with time zone not null default now(),
  constraint chapter_comments_pkey PRIMARY KEY (id),
  constraint chapter_comments_author_id_fkey FOREIGN KEY (author_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint chapter_comments_chapter_id_fkey FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
  constraint chapter_comments_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES chapter_comments(id) ON DELETE CASCADE,
  constraint chapter_comments_body_check CHECK (((char_length(TRIM(BOTH FROM body)) >= 1) AND (char_length(TRIM(BOTH FROM body)) <= 5000))),
  constraint chapter_comments_paragraph_index_check CHECK ((paragraph_index >= 0))
);
alter table public.chapter_comments enable row level security;

create table if not exists public.chapter_likes (
  chapter_id uuid not null,
  user_id uuid not null,
  book_id uuid,
  created_at timestamp with time zone not null default now(),
  constraint chapter_likes_pkey PRIMARY KEY (chapter_id, user_id),
  constraint chapter_likes_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  constraint chapter_likes_chapter_id_fkey FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
  constraint chapter_likes_user_id_fkey FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE
);
alter table public.chapter_likes enable row level security;

create table if not exists public.chapters (
  id uuid not null default gen_random_uuid(),
  book_id uuid not null,
  title text not null default ''::text,
  content text not null default ''::text,
  word_count integer not null default 0,
  order_index integer not null default 0,
  location_tag text not null default ''::text,
  time_tag text not null default ''::text,
  character_tags text[] not null default '{}'::text[],
  author_notes text not null default ''::text,
  updated_at timestamp with time zone not null default now(),
  is_draft boolean not null default false,
  published_at timestamp with time zone,
  constraint chapters_pkey PRIMARY KEY (id),
  constraint chapters_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);
alter table public.chapters enable row level security;

create table if not exists public.characters (
  id uuid not null default gen_random_uuid(),
  book_id uuid not null,
  name text not null,
  role text not null default ''::text,
  description text not null default ''::text,
  traits text not null default ''::text,
  order_index integer not null default 0,
  constraint characters_pkey PRIMARY KEY (id),
  constraint characters_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);
alter table public.characters enable row level security;

create table if not exists public.comments (
  id uuid not null default gen_random_uuid(),
  chapter_id uuid not null,
  author_id uuid not null,
  paragraph_index integer not null default 0,
  text text not null,
  is_pinned boolean not null default false,
  created_at timestamp with time zone not null default now(),
  constraint comments_pkey PRIMARY KEY (id),
  constraint comments_author_id_fkey FOREIGN KEY (author_id) REFERENCES profiles(id) ON DELETE CASCADE,
  constraint comments_chapter_id_fkey FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
);
alter table public.comments enable row level security;

create table if not exists public.community_messages (
  id uuid not null default gen_random_uuid(),
  sender_id uuid not null,
  recipient_id uuid not null,
  body text not null,
  read_at timestamp with time zone,
  created_at timestamp with time zone not null default now(),
  push_sent_at timestamp with time zone,
  constraint community_messages_pkey PRIMARY KEY (id),
  constraint community_messages_recipient_id_fkey FOREIGN KEY (recipient_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint community_messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint community_messages_body_check CHECK (((char_length(TRIM(BOTH FROM body)) >= 1) AND (char_length(TRIM(BOTH FROM body)) <= 5000))),
  constraint community_messages_check CHECK ((sender_id <> recipient_id))
);
alter table public.community_messages enable row level security;

create table if not exists public.community_moderators (
  user_id uuid not null,
  created_at timestamp with time zone not null default now(),
  constraint community_moderators_pkey PRIMARY KEY (user_id),
  constraint community_moderators_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);
alter table public.community_moderators enable row level security;

create table if not exists public.community_notifications (
  id uuid not null default gen_random_uuid(),
  recipient_id uuid not null,
  actor_id uuid,
  kind text not null,
  book_id uuid,
  comment_id uuid,
  message_id uuid,
  announcement_id uuid,
  payload jsonb not null default '{}'::jsonb,
  read_at timestamp with time zone,
  created_at timestamp with time zone not null default now(),
  push_sent_at timestamp with time zone,
  constraint community_notifications_pkey PRIMARY KEY (id),
  constraint community_notifications_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES auth.users(id) ON DELETE SET NULL,
  constraint community_notifications_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  constraint community_notifications_comment_id_fkey FOREIGN KEY (comment_id) REFERENCES chapter_comments(id) ON DELETE CASCADE,
  constraint community_notifications_message_id_fkey FOREIGN KEY (message_id) REFERENCES community_messages(id) ON DELETE CASCADE,
  constraint community_notifications_recipient_id_fkey FOREIGN KEY (recipient_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint community_notifications_kind_check CHECK ((kind = ANY (ARRAY['FOLLOW'::text, 'LIKE'::text, 'COMMENT'::text, 'REPLY'::text, 'MESSAGE'::text, 'ANNOUNCEMENT'::text, 'RATING'::text, 'NEW_CHAPTER'::text, 'NEW_BOOK'::text, 'CIRCLE_POST'::text, 'CHALLENGE'::text, 'OFFICIAL_UPDATE'::text])))
);
alter table public.community_notifications enable row level security;

create table if not exists public.community_reports (
  id uuid not null default gen_random_uuid(),
  reporter_id uuid not null,
  comment_id uuid,
  reported_user_id uuid,
  reason text not null,
  status text not null default 'OPEN'::text,
  moderator_note text,
  created_at timestamp with time zone not null default now(),
  constraint community_reports_pkey PRIMARY KEY (id),
  constraint community_reports_comment_id_fkey FOREIGN KEY (comment_id) REFERENCES chapter_comments(id) ON DELETE CASCADE,
  constraint community_reports_reported_user_id_fkey FOREIGN KEY (reported_user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint community_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint community_reports_check CHECK (((comment_id IS NOT NULL) OR (reported_user_id IS NOT NULL))),
  constraint community_reports_reason_check CHECK (((char_length(TRIM(BOTH FROM reason)) >= 1) AND (char_length(TRIM(BOTH FROM reason)) <= 500))),
  constraint community_reports_status_check CHECK ((status = ANY (ARRAY['OPEN'::text, 'REVIEWING'::text, 'RESOLVED'::text, 'REJECTED'::text])))
);
alter table public.community_reports enable row level security;

create table if not exists public.device_push_tokens (
  user_id uuid not null,
  token text not null,
  platform text not null default 'ANDROID'::text,
  notifications_enabled boolean not null default true,
  updated_at timestamp with time zone not null default now(),
  constraint device_push_tokens_pkey PRIMARY KEY (user_id, token),
  constraint device_push_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint device_push_tokens_platform_check CHECK ((platform = 'ANDROID'::text))
);
alter table public.device_push_tokens enable row level security;

create table if not exists public.favorites (
  user_id uuid not null,
  book_id uuid not null,
  constraint favorites_pkey PRIMARY KEY (user_id, book_id),
  constraint favorites_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  constraint favorites_user_id_fkey FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE
);
alter table public.favorites enable row level security;

create table if not exists public.follows (
  follower_id uuid not null,
  author_id uuid not null,
  constraint follows_pkey PRIMARY KEY (follower_id, author_id),
  constraint follows_author_id_fkey FOREIGN KEY (author_id) REFERENCES profiles(id) ON DELETE CASCADE,
  constraint follows_follower_id_fkey FOREIGN KEY (follower_id) REFERENCES profiles(id) ON DELETE CASCADE,
  constraint follows_check CHECK ((follower_id <> author_id))
);
alter table public.follows enable row level security;

create table if not exists public.messages (
  id uuid not null default gen_random_uuid(),
  sender_id uuid not null,
  recipient_id uuid not null,
  content text not null,
  read boolean not null default false,
  created_at timestamp with time zone not null default now(),
  constraint messages_pkey PRIMARY KEY (id),
  constraint messages_recipient_id_fkey FOREIGN KEY (recipient_id) REFERENCES profiles(id) ON DELETE CASCADE,
  constraint messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES profiles(id) ON DELETE CASCADE
);
alter table public.messages enable row level security;

create table if not exists public.profiles (
  id uuid not null,
  username text,
  display_name text not null default ''::text,
  bio text not null default ''::text,
  avatar_url text not null default ''::text,
  banner_url text not null default ''::text,
  social_links jsonb not null default '[]'::jsonb,
  created_at timestamp with time zone not null default now(),
  constraint profiles_username_key UNIQUE (username),
  constraint profiles_pkey PRIMARY KEY (id),
  constraint profiles_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE
);
alter table public.profiles enable row level security;

create table if not exists public.public_profiles (
  user_id uuid not null,
  display_name text not null,
  username text not null,
  avatar_url text,
  updated_at timestamp with time zone not null default now(),
  banner_url text,
  bio text not null default ''::text,
  links jsonb not null default '[]'::jsonb,
  paypal_me text not null default ''::text,
  constraint public_profiles_username_key UNIQUE (username),
  constraint public_profiles_pkey PRIMARY KEY (user_id),
  constraint public_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint public_profiles_bio_length CHECK ((char_length(bio) <= 2000)),
  constraint public_profiles_display_name_check CHECK (((char_length(TRIM(BOTH FROM display_name)) >= 1) AND (char_length(TRIM(BOTH FROM display_name)) <= 80))),
  constraint public_profiles_links_check CHECK (((jsonb_typeof(links) = 'array'::text) AND (jsonb_array_length(links) <= 10) AND (octet_length((links)::text) <= 4000))),
  constraint public_profiles_links_valid CHECK (regelo_valid_profile_links(links)),
  constraint public_profiles_paypal_me_check CHECK (((char_length(paypal_me) <= 60) AND (paypal_me ~ '^[A-Za-z0-9._-]*$'::text))),
  constraint public_profiles_username_check CHECK (((char_length(TRIM(BOTH FROM username)) >= 1) AND (char_length(TRIM(BOTH FROM username)) <= 40)))
);
alter table public.public_profiles enable row level security;

create table if not exists public.ratings (
  book_id uuid not null,
  user_id uuid not null,
  stars integer not null,
  review text not null default ''::text,
  created_at timestamp with time zone not null default now(),
  constraint ratings_pkey PRIMARY KEY (book_id, user_id),
  constraint ratings_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  constraint ratings_user_id_fkey FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE,
  constraint ratings_stars_check CHECK (((stars >= 1) AND (stars <= 5)))
);
alter table public.ratings enable row level security;

create table if not exists public.reader_library_items (
  user_id uuid not null,
  kind text not null,
  item_id text not null,
  payload jsonb not null default '{}'::jsonb,
  is_deleted boolean not null default false,
  updated_at bigint not null,
  constraint reader_library_items_pkey PRIMARY KEY (user_id, kind, item_id),
  constraint reader_library_items_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint reader_library_items_item_id_check CHECK (((char_length(item_id) >= 1) AND (char_length(item_id) <= 200))),
  constraint reader_library_items_kind_check CHECK ((kind = ANY (ARRAY['FAVORITE'::text, 'COLLECTION'::text, 'NOTE'::text, 'FINISHED'::text, 'GOAL'::text]))),
  constraint reader_library_items_payload_check CHECK (((jsonb_typeof(payload) = 'object'::text) AND (octet_length((payload)::text) <= 20000))),
  constraint reader_library_items_updated_at_check CHECK ((updated_at >= 0))
);
alter table public.reader_library_items enable row level security;

create table if not exists public.reader_progress (
  user_id uuid not null,
  book_id uuid not null,
  chapter_index integer not null,
  percent integer not null,
  is_deleted boolean not null default false,
  updated_at bigint not null,
  chapter_id uuid,
  constraint reader_progress_pkey PRIMARY KEY (user_id, book_id),
  constraint reader_progress_chapter_id_fkey FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE SET NULL,
  constraint reader_progress_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint reader_progress_chapter_index_check CHECK ((chapter_index >= 0)),
  constraint reader_progress_percent_check CHECK (((percent >= 0) AND (percent <= 100))),
  constraint reader_progress_updated_at_check CHECK ((updated_at >= 0))
);
alter table public.reader_progress enable row level security;

create table if not exists public.reader_saved_books (
  user_id uuid not null,
  book_id uuid not null,
  is_saved boolean not null,
  updated_at bigint not null,
  constraint reader_saved_books_pkey PRIMARY KEY (user_id, book_id),
  constraint reader_saved_books_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint reader_saved_books_updated_at_check CHECK ((updated_at >= 0))
);
alter table public.reader_saved_books enable row level security;

create table if not exists public.reading_circle_members (
  circle_id uuid not null,
  user_id uuid not null,
  role text not null default 'MEMBER'::text,
  muted boolean not null default false,
  joined_at timestamp with time zone not null default now(),
  constraint reading_circle_members_pkey PRIMARY KEY (circle_id, user_id),
  constraint reading_circle_members_circle_id_fkey FOREIGN KEY (circle_id) REFERENCES reading_circles(id) ON DELETE CASCADE,
  constraint reading_circle_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint reading_circle_members_role_check CHECK ((role = ANY (ARRAY['OWNER'::text, 'MEMBER'::text])))
);
alter table public.reading_circle_members enable row level security;

create table if not exists public.reading_circle_posts (
  id uuid not null default gen_random_uuid(),
  circle_id uuid not null,
  author_id uuid not null,
  parent_id uuid,
  body text not null,
  deleted_at timestamp with time zone,
  created_at timestamp with time zone not null default now(),
  constraint reading_circle_posts_pkey PRIMARY KEY (id),
  constraint reading_circle_posts_author_id_fkey FOREIGN KEY (author_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint reading_circle_posts_circle_id_fkey FOREIGN KEY (circle_id) REFERENCES reading_circles(id) ON DELETE CASCADE,
  constraint reading_circle_posts_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES reading_circle_posts(id) ON DELETE CASCADE,
  constraint reading_circle_posts_body_check CHECK (((char_length(TRIM(BOTH FROM body)) >= 1) AND (char_length(TRIM(BOTH FROM body)) <= 4000)))
);
alter table public.reading_circle_posts enable row level security;

create table if not exists public.reading_circles (
  id uuid not null default gen_random_uuid(),
  owner_id uuid not null,
  name text not null,
  description text not null default ''::text,
  book_id uuid,
  member_count integer not null default 0,
  post_count integer not null default 0,
  last_post_at timestamp with time zone,
  created_at timestamp with time zone not null default now(),
  constraint reading_circles_pkey PRIMARY KEY (id),
  constraint reading_circles_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE SET NULL,
  constraint reading_circles_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint reading_circles_description_check CHECK ((char_length(description) <= 1000)),
  constraint reading_circles_name_check CHECK (((char_length(TRIM(BOTH FROM name)) >= 3) AND (char_length(TRIM(BOTH FROM name)) <= 60)))
);
alter table public.reading_circles enable row level security;

create table if not exists public.reading_progress (
  user_id uuid not null,
  book_id uuid not null,
  chapter_index integer not null default 0,
  percent integer not null default 0,
  updated_at timestamp with time zone not null default now(),
  constraint reading_progress_pkey PRIMARY KEY (user_id, book_id),
  constraint reading_progress_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  constraint reading_progress_user_id_fkey FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE
);
alter table public.reading_progress enable row level security;

create table if not exists public.regelo_app_config (
  key text not null,
  value jsonb not null,
  updated_at timestamp with time zone not null default now(),
  constraint regelo_app_config_pkey PRIMARY KEY (key)
);
alter table public.regelo_app_config enable row level security;

create table if not exists public.regelo_official_accounts (
  user_id uuid not null,
  label text not null default 'Hivatalos'::text,
  created_at timestamp with time zone not null default now(),
  constraint regelo_official_accounts_pkey PRIMARY KEY (user_id),
  constraint regelo_official_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);
alter table public.regelo_official_accounts enable row level security;

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

create table if not exists public.regelo_roles (
  user_id uuid not null,
  role text not null,
  granted_at timestamp with time zone not null default now(),
  constraint regelo_roles_pkey PRIMARY KEY (user_id, role),
  constraint regelo_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint regelo_roles_role_check CHECK ((role = ANY (ARRAY['official_editor'::text, 'moderator'::text, 'admin'::text])))
);
alter table public.regelo_roles enable row level security;

create table if not exists public.reports (
  id uuid not null default gen_random_uuid(),
  reporter_id uuid not null,
  target_type text not null,
  target_id uuid not null,
  reason text not null,
  note text not null default ''::text,
  resolved boolean not null default false,
  created_at timestamp with time zone not null default now(),
  constraint reports_pkey PRIMARY KEY (id),
  constraint reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES profiles(id) ON DELETE CASCADE
);
alter table public.reports enable row level security;

create table if not exists public.series (
  id uuid not null default gen_random_uuid(),
  author_id uuid not null,
  name text not null,
  description text not null default ''::text,
  created_at timestamp with time zone not null default now(),
  constraint series_pkey PRIMARY KEY (id),
  constraint series_author_id_fkey FOREIGN KEY (author_id) REFERENCES profiles(id) ON DELETE CASCADE
);
alter table public.series enable row level security;

create table if not exists public.timeline_events (
  id uuid not null default gen_random_uuid(),
  book_id uuid not null,
  title text not null,
  description text not null default ''::text,
  order_index integer not null default 0,
  constraint timeline_events_pkey PRIMARY KEY (id),
  constraint timeline_events_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);
alter table public.timeline_events enable row level security;

create table if not exists public.user_follows (
  follower_id uuid not null,
  followed_author_id uuid not null,
  created_at timestamp with time zone not null default now(),
  constraint user_follows_pkey PRIMARY KEY (follower_id, followed_author_id),
  constraint user_follows_followed_author_id_fkey FOREIGN KEY (followed_author_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint user_follows_follower_id_fkey FOREIGN KEY (follower_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint user_follows_check CHECK ((follower_id <> followed_author_id))
);
alter table public.user_follows enable row level security;

create table if not exists public.writing_challenge_entries (
  challenge_id uuid not null,
  book_id uuid not null,
  author_id uuid not null,
  note text not null default ''::text,
  created_at timestamp with time zone not null default now(),
  constraint writing_challenge_entries_one_per_author UNIQUE (challenge_id, author_id),
  constraint writing_challenge_entries_pkey PRIMARY KEY (challenge_id, book_id),
  constraint writing_challenge_entries_author_id_fkey FOREIGN KEY (author_id) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint writing_challenge_entries_book_id_fkey FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
  constraint writing_challenge_entries_challenge_id_fkey FOREIGN KEY (challenge_id) REFERENCES writing_challenges(id) ON DELETE CASCADE,
  constraint writing_challenge_entries_note_check CHECK ((char_length(note) <= 500))
);
alter table public.writing_challenge_entries enable row level security;

create table if not exists public.writing_challenge_votes (
  challenge_id uuid not null,
  book_id uuid not null,
  voter_id uuid not null,
  created_at timestamp with time zone not null default now(),
  constraint writing_challenge_votes_pkey PRIMARY KEY (challenge_id, voter_id),
  constraint writing_challenge_votes_challenge_id_book_id_fkey FOREIGN KEY (challenge_id, book_id) REFERENCES writing_challenge_entries(challenge_id, book_id) ON DELETE CASCADE,
  constraint writing_challenge_votes_voter_id_fkey FOREIGN KEY (voter_id) REFERENCES auth.users(id) ON DELETE CASCADE
);
alter table public.writing_challenge_votes enable row level security;

create table if not exists public.writing_challenges (
  id uuid not null default gen_random_uuid(),
  created_by uuid not null,
  title text not null,
  description text not null default ''::text,
  theme_tag text not null default ''::text,
  min_words integer not null default 0,
  max_words integer not null default 0,
  starts_at timestamp with time zone not null default now(),
  submission_deadline timestamp with time zone not null,
  voting_deadline timestamp with time zone not null,
  created_at timestamp with time zone not null default now(),
  constraint writing_challenges_pkey PRIMARY KEY (id),
  constraint writing_challenges_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth.users(id) ON DELETE CASCADE,
  constraint writing_challenges_dates_check CHECK (((submission_deadline > starts_at) AND (voting_deadline > submission_deadline))),
  constraint writing_challenges_description_check CHECK ((char_length(description) <= 4000)),
  constraint writing_challenges_title_check CHECK (((char_length(TRIM(BOTH FROM title)) >= 3) AND (char_length(TRIM(BOTH FROM title)) <= 100))),
  constraint writing_challenges_words_check CHECK (((min_words >= 0) AND (max_words >= 0) AND ((max_words = 0) OR (max_words >= min_words))))
);
alter table public.writing_challenges enable row level security;

-- ===================== NÉZETEK =====================
create or replace view public.book_rating_stats as
 SELECT book_id,
    count(*)::integer AS rating_count,
    round(avg(stars), 2)::double precision AS rating_avg
   FROM book_ratings
  GROUP BY book_id;

create or replace view public.chapter_like_stats as
 SELECT chapter_id,
    book_id,
    count(*)::integer AS like_count
   FROM chapter_likes
  GROUP BY chapter_id, book_id;

-- ===================== INDEXEK =====================
create index if not exists app_error_reports_created_idx ON public.app_error_reports USING btree (created_at DESC);
create index if not exists app_error_reports_user_id_fk_idx ON public.app_error_reports USING btree (user_id);
create index if not exists author_announcements_author_idx ON public.author_announcements USING btree (author_id, created_at DESC);
create index if not exists author_announcements_book_id_fk_idx ON public.author_announcements USING btree (book_id);
create index if not exists blocked_users_blocked_idx ON public.blocked_users USING btree (blocked_id);
create index if not exists book_likes_book_id_fk_idx ON public.book_likes USING btree (book_id);
create index if not exists book_ratings_book_idx ON public.book_ratings USING btree (book_id, updated_at DESC);
create index if not exists book_views_book_id_fk_idx ON public.book_views USING btree (book_id);
create index if not exists books_author_idx ON public.books USING btree (author_id);
create index if not exists books_published_idx ON public.books USING btree (publish_status, updated_at DESC);
create index if not exists books_series_id_fk_idx ON public.books USING btree (series_id);
create index if not exists chapter_comments_author_id_fk_idx ON public.chapter_comments USING btree (author_id);
create index if not exists chapter_comments_chapter_idx ON public.chapter_comments USING btree (chapter_id, created_at);
create index if not exists chapter_comments_parent_id_fk_idx ON public.chapter_comments USING btree (parent_id);
create index if not exists chapter_likes_book_idx ON public.chapter_likes USING btree (book_id);
create index if not exists chapter_likes_chapter_idx ON public.chapter_likes USING btree (chapter_id);
create index if not exists chapter_likes_user_id_fk_idx ON public.chapter_likes USING btree (user_id);
create index if not exists chapters_book_order_idx ON public.chapters USING btree (book_id, order_index);
create index if not exists chapters_published_idx ON public.chapters USING btree (published_at DESC) WHERE (published_at IS NOT NULL);
create index if not exists characters_book_id_fk_idx ON public.characters USING btree (book_id);
create index if not exists comments_author_id_fk_idx ON public.comments USING btree (author_id);
create index if not exists comments_chapter_idx ON public.comments USING btree (chapter_id);
create index if not exists community_messages_pair_idx ON public.community_messages USING btree (sender_id, recipient_id, created_at DESC);
create index if not exists community_messages_recipient_idx ON public.community_messages USING btree (recipient_id, created_at DESC);
create index if not exists community_notifications_actor_id_fk_idx ON public.community_notifications USING btree (actor_id);
create index if not exists community_notifications_book_id_fk_idx ON public.community_notifications USING btree (book_id);
create index if not exists community_notifications_comment_id_fk_idx ON public.community_notifications USING btree (comment_id);
create index if not exists community_notifications_message_id_fk_idx ON public.community_notifications USING btree (message_id);
create index if not exists community_notifications_recipient_idx ON public.community_notifications USING btree (recipient_id, created_at DESC);
create index if not exists community_reports_comment_id_fk_idx ON public.community_reports USING btree (comment_id);
create index if not exists community_reports_reported_user_id_fk_idx ON public.community_reports USING btree (reported_user_id);
create index if not exists community_reports_reporter_id_fk_idx ON public.community_reports USING btree (reporter_id);
create index if not exists favorites_book_id_fk_idx ON public.favorites USING btree (book_id);
create index if not exists follows_author_id_fk_idx ON public.follows USING btree (author_id);
create index if not exists messages_recipient_id_fk_idx ON public.messages USING btree (recipient_id);
create index if not exists messages_thread_idx ON public.messages USING btree (sender_id, recipient_id, created_at DESC);
create index if not exists public_profiles_username_idx ON public.public_profiles USING btree (username);
create index if not exists ratings_user_id_fk_idx ON public.ratings USING btree (user_id);
create index if not exists reader_progress_chapter_id_fk_idx ON public.reader_progress USING btree (chapter_id);
create index if not exists reading_circle_members_user_idx ON public.reading_circle_members USING btree (user_id);
create index if not exists reading_circle_posts_author_id_fk_idx ON public.reading_circle_posts USING btree (author_id);
create index if not exists reading_circle_posts_circle_idx ON public.reading_circle_posts USING btree (circle_id, created_at DESC);
create index if not exists reading_circle_posts_parent_id_fk_idx ON public.reading_circle_posts USING btree (parent_id);
create index if not exists reading_circles_book_id_fk_idx ON public.reading_circles USING btree (book_id);
create index if not exists reading_circles_owner_id_fk_idx ON public.reading_circles USING btree (owner_id);
create index if not exists reading_progress_book_id_fk_idx ON public.reading_progress USING btree (book_id);
create index if not exists regelo_official_subscriptions_book_id_fk_idx ON public.regelo_official_subscriptions USING btree (book_id);
create index if not exists regelo_rate_events_idx ON public.regelo_rate_events USING btree (action, user_id, at DESC);
create index if not exists reports_reporter_id_fk_idx ON public.reports USING btree (reporter_id);
create index if not exists series_author_id_fk_idx ON public.series USING btree (author_id);
create index if not exists timeline_events_book_id_fk_idx ON public.timeline_events USING btree (book_id);
create index if not exists user_follows_followed_idx ON public.user_follows USING btree (followed_author_id);
create index if not exists writing_challenge_entries_author_id_fk_idx ON public.writing_challenge_entries USING btree (author_id);
create index if not exists writing_challenge_entries_book_id_fk_idx ON public.writing_challenge_entries USING btree (book_id);
create index if not exists writing_challenge_votes_entry_fk_idx ON public.writing_challenge_votes USING btree (challenge_id, book_id);
create index if not exists writing_challenge_votes_voter_id_fk_idx ON public.writing_challenge_votes USING btree (voter_id);
create index if not exists writing_challenges_created_by_fk_idx ON public.writing_challenges USING btree (created_by);

-- ===================== FÜGGVÉNYEK =====================
-- FUNCTION delete_own_account()  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.delete_own_account()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  if auth.uid() is null then
    raise exception 'Nincs bejelentkezve';
  end if;
  -- A profiles törlése kaszkádolva mindent visz (books → chapters → comments stb.)
  delete from auth.users where id = auth.uid();
end;
$function$;

-- FUNCTION handle_new_user()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.handle_new_user()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'display_name', ''));
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

-- FUNCTION regelo_blocked_me(p_other uuid)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_blocked_me(p_other uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
    select p_other is not null and auth.uid() is not null and exists (
        select 1 from public.blocked_users b
        where b.user_id = p_other and b.blocked_id = auth.uid()
    );
$function$;

-- FUNCTION regelo_book_published_at()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_book_published_at()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if new.publish_status = 'PUBLISHED' and new.published_at is null then
    new.published_at := now();
  end if;
  return new;
end;
$function$;

-- FUNCTION regelo_book_stats(p_book_id uuid, p_days integer)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_book_stats(p_book_id uuid, p_days integer DEFAULT 30)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
$function$;

-- FUNCTION regelo_books_touch()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_books_touch()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
begin
  if (to_jsonb(new) - 'updated_at' - 'view_count' - 'published_at') is distinct from
     (to_jsonb(old) - 'updated_at' - 'view_count' - 'published_at') then
    new.updated_at := now();
  end if;
  return new;
end;
$function$;

-- FUNCTION regelo_can_join_circle(p_circle uuid)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_can_join_circle(p_circle uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select auth.uid() is not null and exists (select 1 from public.reading_circles c where c.id = p_circle)
     and not exists (select 1 from public.reading_circles c join public.blocked_users b
                      on b.user_id = c.owner_id and b.blocked_id = auth.uid() where c.id = p_circle);
$function$;

-- FUNCTION regelo_challenge_results(p_challenge uuid)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_challenge_results(p_challenge uuid)
 RETURNS TABLE(book_id uuid, author_id uuid, votes integer, rank integer)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
$function$;

-- FUNCTION regelo_challenge_winners()  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_challenge_winners()
 RETURNS TABLE(challenge_id uuid, book_id uuid, author_id uuid, votes integer)
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
$function$;

-- FUNCTION regelo_chapter_like_book()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_chapter_like_book()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
    select c.book_id into new.book_id from public.chapters c where c.id = new.chapter_id;
    return new;
end;
$function$;

-- FUNCTION regelo_chapter_published_at()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_chapter_published_at()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if new.is_draft = false and new.published_at is null
     and exists (select 1 from public.books b where b.id = new.book_id and b.publish_status = 'PUBLISHED') then
    new.published_at := now();
  end if;
  return new;
end;
$function$;

-- FUNCTION regelo_chapters_touch_book()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_chapters_touch_book()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  if tg_op = 'UPDATE' and (to_jsonb(new) - 'published_at') is not distinct from (to_jsonb(old) - 'published_at') then
    return null;
  end if;
  update public.books
     set updated_at = now()
   where id = case when tg_op = 'DELETE' then old.book_id else new.book_id end;
  return null;
end;
$function$;

-- FUNCTION regelo_circle_created()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_circle_created()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  insert into public.reading_circle_members (circle_id, user_id, role) values (new.id, new.owner_id, 'OWNER')
  on conflict (circle_id, user_id) do update set role = 'OWNER';
  return null;
end;
$function$;

-- FUNCTION regelo_circle_member_count()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_circle_member_count()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_circle uuid := coalesce(new.circle_id, old.circle_id);
begin
  update public.reading_circles
     set member_count = (select count(*) from public.reading_circle_members m where m.circle_id = v_circle)
   where id = v_circle;
  return null;
end;
$function$;

-- FUNCTION regelo_circle_post_changed()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_circle_post_changed()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
    insert into public.community_notifications (recipient_id, actor_id, kind, payload)
    select m.user_id, new.author_id, 'CIRCLE_POST',
           jsonb_build_object('circle_id', new.circle_id, 'circle_name', v_circle.name, 'post_id', new.id,
                              'text', left(new.body, 300))
      from public.reading_circle_members m
     where m.circle_id = new.circle_id and m.user_id <> new.author_id and m.muted = false;
  else
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
$function$;

-- FUNCTION regelo_circle_post_guard()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_circle_post_guard()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$
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

-- FUNCTION regelo_create_public_profile()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_create_public_profile()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    profile_name text;
    profile_username text;
begin
    profile_name := left(
        coalesce(
            nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''),
            'Új olvasó'
        ),
        80
    );
    profile_username := public.regelo_profile_username(
        new.id,
        coalesce(new.raw_user_meta_data ->> 'username', 'iro')
    );

    insert into public.public_profiles(user_id, display_name, username)
    values (new.id, profile_name, profile_username)
    on conflict (user_id) do nothing;
    return new;
end;
$function$;

-- FUNCTION regelo_dispatch_push()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_dispatch_push()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
$function$;

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

-- FUNCTION regelo_has_role(p_role text)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_has_role(p_role text)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select exists (select 1 from public.regelo_roles where user_id = (select auth.uid()) and role = p_role)
$function$;

-- FUNCTION regelo_hu_time(t timestamp with time zone)  EXECUTE: -,authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_hu_time(t timestamp with time zone)
 RETURNS text
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$
  select to_char(t at time zone 'Europe/Budapest', 'YYYY. MM. DD. HH24:MI')
$function$;

-- FUNCTION regelo_i_blocked(p_other uuid)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_i_blocked(p_other uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
    select p_other is not null and auth.uid() is not null and exists (
        select 1 from public.blocked_users b
        where b.user_id = auth.uid() and b.blocked_id = p_other
    );
$function$;

-- FUNCTION regelo_is_blocked(blocker uuid, target uuid)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_is_blocked(blocker uuid, target uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
    select blocker is not null and target is not null and exists (
        select 1 from public.blocked_users b where b.user_id = blocker and b.blocked_id = target
    );
$function$;

-- FUNCTION regelo_is_circle_member(p_circle uuid)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_is_circle_member(p_circle uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select auth.uid() is not null and exists (
    select 1 from public.reading_circle_members m where m.circle_id = p_circle and m.user_id = auth.uid());
$function$;

-- FUNCTION regelo_is_circle_owner(p_circle uuid)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_is_circle_owner(p_circle uuid)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
  select auth.uid() is not null and exists (
    select 1 from public.reading_circles c where c.id = p_circle and c.owner_id = auth.uid());
$function$;

-- FUNCTION regelo_is_moderator()  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_is_moderator()
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
    select exists (select 1 from public.community_moderators where user_id = auth.uid());
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

-- FUNCTION regelo_notify_challenge()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_notify_challenge()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
  insert into public.community_notifications (recipient_id, actor_id, kind, payload)
  select p.user_id, new.created_by, 'CHALLENGE', jsonb_build_object('challenge_id', new.id, 'title', new.title)
    from public.public_profiles p
   where p.user_id <> new.created_by;
  return null;
end;
$function$;

-- FUNCTION regelo_notify_comment()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_notify_comment()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    owner_id uuid;
    v_book_id uuid;
begin
    select b.author_id, b.id into owner_id, v_book_id
    from public.chapters c join public.books b on b.id = c.book_id
    where c.id = new.chapter_id;
    if owner_id is not null and owner_id <> new.author_id then
        insert into public.community_notifications(recipient_id, actor_id, kind, comment_id, book_id, payload)
        values (owner_id, new.author_id, case when new.parent_id is null then 'COMMENT' else 'REPLY' end,
                new.id, v_book_id, '{}'::jsonb);
    end if;
    if new.parent_id is not null then
        insert into public.community_notifications(recipient_id, actor_id, kind, comment_id, book_id, payload)
        select parent.author_id, new.author_id, 'REPLY', new.id, v_book_id, '{}'::jsonb
        from public.chapter_comments parent
        where parent.id = new.parent_id
          and parent.author_id <> new.author_id
          -- a könyv szerzője fent már kapott értesítést erről a válaszról
          and parent.author_id is distinct from owner_id;
    end if;
    return new;
end;
$function$;

-- FUNCTION regelo_notify_follow()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_notify_follow()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
    insert into public.community_notifications(recipient_id, actor_id, kind, payload)
    values (new.followed_author_id, new.follower_id, 'FOLLOW', '{}'::jsonb);
    return new;
end;
$function$;

-- FUNCTION regelo_notify_like()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_notify_like()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare owner_id uuid;
begin
    select author_id into owner_id from public.books where id = new.book_id;
    if owner_id is not null and owner_id <> new.user_id then
        insert into public.community_notifications(recipient_id, actor_id, kind, book_id, payload)
        values (owner_id, new.user_id, 'LIKE', new.book_id, '{}'::jsonb);
    end if;
    return new;
end;
$function$;

-- FUNCTION regelo_notify_message()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_notify_message()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
    insert into public.community_notifications(recipient_id, actor_id, kind, message_id, payload)
    values (new.recipient_id, new.sender_id, 'MESSAGE', new.id, '{}'::jsonb);
    return new;
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

-- FUNCTION regelo_notify_rating()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_notify_rating()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare owner_id uuid;
begin
    select author_id into owner_id from public.books where id = new.book_id;
    if owner_id is not null and owner_id <> new.user_id then
        insert into public.community_notifications(recipient_id, actor_id, kind, book_id, payload)
        values (owner_id, new.user_id, 'RATING', new.book_id, jsonb_build_object('stars', new.stars::text));
    end if;
    return new;
end;
$function$;

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

-- FUNCTION regelo_profile_username(p_user_id uuid, p_preferred text)  EXECUTE: -,authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_profile_username(p_user_id uuid, p_preferred text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO 'public'
AS $function$
    with normalized as (
        select left(
            nullif(regexp_replace(lower(coalesce(p_preferred, '')), '[^a-z0-9_.]', '', 'g'), ''),
            30
        ) as base
    )
    select left(coalesce(base, 'iro'), 7) || '_' || replace(p_user_id::text, '-', '')
    from normalized;
$function$;

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

-- FUNCTION regelo_publish_scheduled()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_publish_scheduled()
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
    if not exists (select 1 from public.chapters c where c.book_id = r.id and c.is_draft = false) then
      update public.chapters set is_draft = false, updated_at = now() where book_id = r.id;
    end if;
    update public.books set publish_status = 'PUBLISHED' where id = r.id;
    v_count := v_count + 1;
  end loop;
  perform set_config('regelo.system_push', '', true);
  return v_count;
end;
$function$;

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

-- FUNCTION regelo_record_book_view(p_book_id uuid)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_record_book_view(p_book_id uuid)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    viewer uuid := auth.uid();
    owner uuid;
    status text;
    inserted integer;
    current_count integer;
begin
    select author_id, publish_status, view_count into owner, status, current_count from public.books where id = p_book_id;
    if owner is null then return 0; end if;
    if viewer is null or viewer = owner or status <> 'PUBLISHED' then return current_count; end if;
    insert into public.book_views(user_id, book_id, viewed_on) values (viewer, p_book_id, current_date) on conflict do nothing;
    get diagnostics inserted = row_count;
    if inserted > 0 then
        update public.books set view_count = view_count + 1 where id = p_book_id returning view_count into current_count;
    end if;
    return current_count;
end;
$function$;

-- FUNCTION regelo_register_push_token(p_token text, p_enabled boolean)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_register_push_token(p_token text, p_enabled boolean DEFAULT true)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'Bejelentkezés szükséges.' using errcode = '42501';
    end if;
    if p_token is null or char_length(p_token) < 20 or char_length(p_token) > 4096 then
        raise exception 'Érvénytelen eszköztoken.' using errcode = '22023';
    end if;
    delete from public.device_push_tokens where token = p_token and user_id <> v_uid;
    insert into public.device_push_tokens(user_id, token, platform, notifications_enabled, updated_at)
    values (v_uid, p_token, 'ANDROID', coalesce(p_enabled, true), now())
    on conflict (user_id, token) do update
        set notifications_enabled = excluded.notifications_enabled,
            updated_at = now();
end;
$function$;

-- FUNCTION regelo_search_profiles(q text)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_search_profiles(q text)
 RETURNS SETOF public_profiles
 LANGUAGE sql
 STABLE
 SET search_path TO 'public', 'extensions'
AS $function$
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
$function$;

-- FUNCTION regelo_server_now()  EXECUTE: -,anon,authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_server_now()
 RETURNS timestamp with time zone
 LANGUAGE sql
 STABLE
 SET search_path TO 'public'
AS $function$ select now() $function$;

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

-- FUNCTION regelo_skip_blocked_notification()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.regelo_skip_blocked_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
    if new.actor_id is not null and public.regelo_is_blocked(new.recipient_id, new.actor_id) then
        return null;
    end if;
    return new;
end;
$function$;

-- FUNCTION regelo_user_badges(p_user uuid)  EXECUTE: authenticated,service_role
CREATE OR REPLACE FUNCTION public.regelo_user_badges(p_user uuid)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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

-- FUNCTION rls_auto_enable()  EXECUTE: service_role
CREATE OR REPLACE FUNCTION public.rls_auto_enable()
 RETURNS event_trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog'
AS $function$
DECLARE
  cmd record;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table','partitioned table')
  LOOP
     IF cmd.schema_name IS NOT NULL AND cmd.schema_name IN ('public') AND cmd.schema_name NOT IN ('pg_catalog','information_schema') AND cmd.schema_name NOT LIKE 'pg_toast%' AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        EXECUTE format('alter table if exists %s enable row level security', cmd.object_identity);
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
     ELSE
        RAISE LOG 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     END IF;
  END LOOP;
END;
$function$;

-- ===================== TRIGGEREK =====================
drop trigger if exists regelo_rate_guard on public.app_error_reports;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.app_error_reports FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_announcement_notification on public.author_announcements;
CREATE TRIGGER regelo_announcement_notification AFTER INSERT ON public.author_announcements FOR EACH ROW EXECUTE FUNCTION regelo_notify_announcement();
drop trigger if exists regelo_clean_guard on public.author_announcements;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF title, body ON public.author_announcements FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_official_announcement_removed on public.author_announcements;
CREATE TRIGGER regelo_official_announcement_removed AFTER DELETE ON public.author_announcements FOR EACH ROW EXECUTE FUNCTION regelo_official_announcement_removed();
drop trigger if exists regelo_official_from_announcement on public.author_announcements;
CREATE TRIGGER regelo_official_from_announcement AFTER INSERT ON public.author_announcements FOR EACH ROW EXECUTE FUNCTION regelo_official_from_announcement();
drop trigger if exists regelo_like_notification on public.book_likes;
CREATE TRIGGER regelo_like_notification AFTER INSERT ON public.book_likes FOR EACH ROW EXECUTE FUNCTION regelo_notify_like();
drop trigger if exists regelo_official_rating_guard on public.book_ratings;
CREATE TRIGGER regelo_official_rating_guard BEFORE INSERT OR UPDATE ON public.book_ratings FOR EACH ROW EXECUTE FUNCTION regelo_official_rating_guard();
drop trigger if exists regelo_rating_notification on public.book_ratings;
CREATE TRIGGER regelo_rating_notification AFTER INSERT ON public.book_ratings FOR EACH ROW EXECUTE FUNCTION regelo_notify_rating();
drop trigger if exists regelo_book_published_at on public.books;
CREATE TRIGGER regelo_book_published_at BEFORE INSERT OR UPDATE ON public.books FOR EACH ROW EXECUTE FUNCTION regelo_book_published_at();
drop trigger if exists regelo_books_touch on public.books;
CREATE TRIGGER regelo_books_touch BEFORE UPDATE ON public.books FOR EACH ROW EXECUTE FUNCTION regelo_books_touch();
drop trigger if exists regelo_clean_guard on public.books;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF title, pen_name, short_description ON public.books FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_notify_new_book on public.books;
CREATE TRIGGER regelo_notify_new_book AFTER INSERT OR UPDATE ON public.books FOR EACH ROW EXECUTE FUNCTION regelo_notify_new_book();
drop trigger if exists regelo_official_book_guard on public.books;
CREATE TRIGGER regelo_official_book_guard BEFORE UPDATE ON public.books FOR EACH ROW EXECUTE FUNCTION regelo_official_book_guard();
drop trigger if exists regelo_clean_guard on public.chapter_comments;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF body ON public.chapter_comments FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_comment_notification on public.chapter_comments;
CREATE TRIGGER regelo_comment_notification AFTER INSERT ON public.chapter_comments FOR EACH ROW EXECUTE FUNCTION regelo_notify_comment();
drop trigger if exists regelo_rate_guard on public.chapter_comments;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.chapter_comments FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_chapter_like_book on public.chapter_likes;
CREATE TRIGGER regelo_chapter_like_book BEFORE INSERT OR UPDATE ON public.chapter_likes FOR EACH ROW EXECUTE FUNCTION regelo_chapter_like_book();
drop trigger if exists regelo_chapter_published_at on public.chapters;
CREATE TRIGGER regelo_chapter_published_at BEFORE INSERT OR UPDATE ON public.chapters FOR EACH ROW EXECUTE FUNCTION regelo_chapter_published_at();
drop trigger if exists regelo_chapters_touch_book on public.chapters;
CREATE TRIGGER regelo_chapters_touch_book AFTER INSERT OR DELETE OR UPDATE ON public.chapters FOR EACH ROW EXECUTE FUNCTION regelo_chapters_touch_book();
drop trigger if exists regelo_clean_guard on public.chapters;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF title ON public.chapters FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_notify_new_chapter on public.chapters;
CREATE TRIGGER regelo_notify_new_chapter AFTER INSERT OR UPDATE ON public.chapters FOR EACH ROW EXECUTE FUNCTION regelo_notify_new_chapter();
drop trigger if exists regelo_clean_guard on public.community_messages;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF body ON public.community_messages FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_message_notification on public.community_messages;
CREATE TRIGGER regelo_message_notification AFTER INSERT ON public.community_messages FOR EACH ROW EXECUTE FUNCTION regelo_notify_message();
drop trigger if exists regelo_rate_guard on public.community_messages;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.community_messages FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_dispatch_push on public.community_notifications;
CREATE TRIGGER regelo_dispatch_push AFTER INSERT ON public.community_notifications FOR EACH ROW EXECUTE FUNCTION regelo_dispatch_push();
drop trigger if exists regelo_skip_blocked_notification on public.community_notifications;
CREATE TRIGGER regelo_skip_blocked_notification BEFORE INSERT ON public.community_notifications FOR EACH ROW EXECUTE FUNCTION regelo_skip_blocked_notification();
drop trigger if exists regelo_rate_guard on public.community_reports;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.community_reports FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_clean_guard on public.public_profiles;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF display_name, bio ON public.public_profiles FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_progress_chapter_guard on public.reader_progress;
CREATE TRIGGER regelo_progress_chapter_guard BEFORE UPDATE ON public.reader_progress FOR EACH ROW EXECUTE FUNCTION regelo_progress_chapter_guard();
drop trigger if exists regelo_circle_member_count on public.reading_circle_members;
CREATE TRIGGER regelo_circle_member_count AFTER INSERT OR DELETE ON public.reading_circle_members FOR EACH ROW EXECUTE FUNCTION regelo_circle_member_count();
drop trigger if exists regelo_circle_post_changed on public.reading_circle_posts;
CREATE TRIGGER regelo_circle_post_changed AFTER INSERT OR UPDATE OF deleted_at ON public.reading_circle_posts FOR EACH ROW EXECUTE FUNCTION regelo_circle_post_changed();
drop trigger if exists regelo_circle_post_guard on public.reading_circle_posts;
CREATE TRIGGER regelo_circle_post_guard BEFORE INSERT OR UPDATE ON public.reading_circle_posts FOR EACH ROW EXECUTE FUNCTION regelo_circle_post_guard();
drop trigger if exists regelo_clean_guard on public.reading_circle_posts;
CREATE TRIGGER regelo_clean_guard BEFORE INSERT OR UPDATE OF body ON public.reading_circle_posts FOR EACH ROW EXECUTE FUNCTION regelo_clean_guard();
drop trigger if exists regelo_rate_guard on public.reading_circle_posts;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.reading_circle_posts FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_circle_created on public.reading_circles;
CREATE TRIGGER regelo_circle_created AFTER INSERT ON public.reading_circles FOR EACH ROW EXECUTE FUNCTION regelo_circle_created();
drop trigger if exists regelo_rate_guard on public.reports;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.reports FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_follow_notification on public.user_follows;
CREATE TRIGGER regelo_follow_notification AFTER INSERT ON public.user_follows FOR EACH ROW EXECUTE FUNCTION regelo_notify_follow();
drop trigger if exists regelo_rate_guard on public.user_follows;
CREATE TRIGGER regelo_rate_guard BEFORE INSERT ON public.user_follows FOR EACH ROW EXECUTE FUNCTION regelo_rate_guard();
drop trigger if exists regelo_notify_challenge on public.writing_challenges;
CREATE TRIGGER regelo_notify_challenge AFTER INSERT ON public.writing_challenges FOR EACH ROW EXECUTE FUNCTION regelo_notify_challenge();

-- ===================== SZABÁLYOK (RLS) =====================
drop policy if exists "app_error_reports_insert" on public.app_error_reports;
create policy "app_error_reports_insert" on public.app_error_reports as permissive for insert to anon,authenticated
  with check ((((user_id IS NULL) OR (user_id = ( SELECT auth.uid() AS uid))) AND (char_length(message) <= 4000) AND (char_length(stack) <= 20000)));
drop policy if exists "announcements_delete" on public.author_announcements;
create policy "announcements_delete" on public.author_announcements as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "announcements_insert" on public.author_announcements;
create policy "announcements_insert" on public.author_announcements as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "announcements_read" on public.author_announcements;
create policy "announcements_read" on public.author_announcements as permissive for select to authenticated
  using (true);
drop policy if exists "announcements_update" on public.author_announcements;
create policy "announcements_update" on public.author_announcements as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = author_id))
  with check ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "blocked_all" on public.blocked_users;
create policy "blocked_all" on public.blocked_users as permissive for all to public
  using ((( SELECT auth.uid() AS uid) = user_id))
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "book_likes_delete" on public.book_likes;
create policy "book_likes_delete" on public.book_likes as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "book_likes_insert" on public.book_likes;
create policy "book_likes_insert" on public.book_likes as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "book_likes_read" on public.book_likes;
create policy "book_likes_read" on public.book_likes as permissive for select to authenticated
  using (true);
drop policy if exists "book_ratings_delete" on public.book_ratings;
create policy "book_ratings_delete" on public.book_ratings as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "book_ratings_insert" on public.book_ratings;
create policy "book_ratings_insert" on public.book_ratings as permissive for insert to authenticated
  with check (((( SELECT auth.uid() AS uid) = user_id) AND (NOT (EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = book_ratings.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))))));
drop policy if exists "book_ratings_read" on public.book_ratings;
create policy "book_ratings_read" on public.book_ratings as permissive for select to authenticated
  using (true);
drop policy if exists "book_ratings_update" on public.book_ratings;
create policy "book_ratings_update" on public.book_ratings as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id))
  with check (((( SELECT auth.uid() AS uid) = user_id) AND (NOT (EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = book_ratings.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))))));
drop policy if exists "books_delete" on public.books;
create policy "books_delete" on public.books as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "books_insert" on public.books;
create policy "books_insert" on public.books as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "books_select" on public.books;
create policy "books_select" on public.books as permissive for select to public
  using (((publish_status = 'PUBLISHED'::text) OR (( SELECT auth.uid() AS uid) = author_id)));
drop policy if exists "books_update" on public.books;
create policy "books_update" on public.books as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = author_id))
  with check ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "chapter_comments_delete" on public.chapter_comments;
create policy "chapter_comments_delete" on public.chapter_comments as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "chapter_comments_insert" on public.chapter_comments;
create policy "chapter_comments_insert" on public.chapter_comments as permissive for insert to authenticated
  with check (((( SELECT auth.uid() AS uid) = author_id) AND (EXISTS ( SELECT 1
   FROM (chapters c
     JOIN books b ON ((b.id = c.book_id)))
  WHERE ((c.id = chapter_comments.chapter_id) AND ((b.author_id = ( SELECT auth.uid() AS uid)) OR ((b.publish_status = 'PUBLISHED'::text) AND (c.is_draft = false) AND (NOT regelo_blocked_me(b.author_id)))))))));
drop policy if exists "chapter_comments_read" on public.chapter_comments;
create policy "chapter_comments_read" on public.chapter_comments as permissive for select to authenticated
  using (((((deleted_at IS NULL) OR (( SELECT auth.uid() AS uid) = author_id)) AND (NOT regelo_i_blocked(author_id))) OR regelo_is_moderator()));
drop policy if exists "chapter_comments_update" on public.chapter_comments;
create policy "chapter_comments_update" on public.chapter_comments as permissive for update to authenticated
  using (((( SELECT auth.uid() AS uid) = author_id) OR regelo_is_moderator()))
  with check (((( SELECT auth.uid() AS uid) = author_id) OR regelo_is_moderator()));
drop policy if exists "chapter_likes_delete" on public.chapter_likes;
create policy "chapter_likes_delete" on public.chapter_likes as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "chapter_likes_insert" on public.chapter_likes;
create policy "chapter_likes_insert" on public.chapter_likes as permissive for insert to authenticated
  with check (((( SELECT auth.uid() AS uid) = user_id) AND (NOT (EXISTS ( SELECT 1
   FROM (chapters c
     JOIN books b ON ((b.id = c.book_id)))
  WHERE ((c.id = chapter_likes.chapter_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))))));
drop policy if exists "chapter_likes_read" on public.chapter_likes;
create policy "chapter_likes_read" on public.chapter_likes as permissive for select to authenticated
  using (true);
drop policy if exists "chapters_delete" on public.chapters;
create policy "chapters_delete" on public.chapters as permissive for delete to authenticated
  using ((EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = chapters.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))));
drop policy if exists "chapters_insert" on public.chapters;
create policy "chapters_insert" on public.chapters as permissive for insert to authenticated
  with check (((EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = chapters.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))) OR ((EXISTS ( SELECT 1
   FROM regelo_official_books o
  WHERE (o.book_id = chapters.book_id))) AND ( SELECT regelo_has_role('official_editor'::text) AS regelo_has_role))));
drop policy if exists "chapters_select" on public.chapters;
create policy "chapters_select" on public.chapters as permissive for select to public
  using ((EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = chapters.book_id) AND ((b.author_id = ( SELECT auth.uid() AS uid)) OR ((b.publish_status = 'PUBLISHED'::text) AND (chapters.is_draft = false)))))));
drop policy if exists "chapters_update" on public.chapters;
create policy "chapters_update" on public.chapters as permissive for update to authenticated
  using (((EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = chapters.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))) OR ((EXISTS ( SELECT 1
   FROM regelo_official_books o
  WHERE (o.book_id = chapters.book_id))) AND ( SELECT regelo_has_role('official_editor'::text) AS regelo_has_role))))
  with check (((EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = chapters.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))) OR ((EXISTS ( SELECT 1
   FROM regelo_official_books o
  WHERE (o.book_id = chapters.book_id))) AND ( SELECT regelo_has_role('official_editor'::text) AS regelo_has_role))));
drop policy if exists "characters_all" on public.characters;
create policy "characters_all" on public.characters as permissive for all to public
  using ((EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = characters.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))))
  with check ((EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = characters.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))));
drop policy if exists "comments_delete" on public.comments;
create policy "comments_delete" on public.comments as permissive for delete to public
  using (((( SELECT auth.uid() AS uid) = author_id) OR (EXISTS ( SELECT 1
   FROM (chapters c
     JOIN books b ON ((b.id = c.book_id)))
  WHERE ((c.id = comments.chapter_id) AND (b.author_id = ( SELECT auth.uid() AS uid)))))));
drop policy if exists "comments_insert" on public.comments;
create policy "comments_insert" on public.comments as permissive for insert to public
  with check ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "comments_select" on public.comments;
create policy "comments_select" on public.comments as permissive for select to public
  using (true);
drop policy if exists "messages_insert" on public.community_messages;
create policy "messages_insert" on public.community_messages as permissive for insert to authenticated
  with check (((( SELECT auth.uid() AS uid) = sender_id) AND (NOT regelo_blocked_me(recipient_id))));
drop policy if exists "messages_read" on public.community_messages;
create policy "messages_read" on public.community_messages as permissive for select to authenticated
  using (((( SELECT auth.uid() AS uid) = sender_id) OR (( SELECT auth.uid() AS uid) = recipient_id)));
drop policy if exists "messages_update_recipient" on public.community_messages;
create policy "messages_update_recipient" on public.community_messages as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = recipient_id))
  with check ((( SELECT auth.uid() AS uid) = recipient_id));
drop policy if exists "moderators_read_own" on public.community_moderators;
create policy "moderators_read_own" on public.community_moderators as permissive for select to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "notifications_read" on public.community_notifications;
create policy "notifications_read" on public.community_notifications as permissive for select to authenticated
  using ((( SELECT auth.uid() AS uid) = recipient_id));
drop policy if exists "notifications_update" on public.community_notifications;
create policy "notifications_update" on public.community_notifications as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = recipient_id))
  with check ((( SELECT auth.uid() AS uid) = recipient_id));
drop policy if exists "community_reports_read" on public.community_reports;
create policy "community_reports_read" on public.community_reports as permissive for select to authenticated
  using (((( SELECT auth.uid() AS uid) = reporter_id) OR (EXISTS ( SELECT 1
   FROM community_moderators m
  WHERE (m.user_id = ( SELECT auth.uid() AS uid))))));
drop policy if exists "reports_insert" on public.community_reports;
create policy "reports_insert" on public.community_reports as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = reporter_id));
drop policy if exists "reports_update_moderators" on public.community_reports;
create policy "reports_update_moderators" on public.community_reports as permissive for update to authenticated
  using ((EXISTS ( SELECT 1
   FROM community_moderators m
  WHERE (m.user_id = ( SELECT auth.uid() AS uid)))))
  with check ((EXISTS ( SELECT 1
   FROM community_moderators m
  WHERE (m.user_id = ( SELECT auth.uid() AS uid)))));
drop policy if exists "push_tokens_delete_own" on public.device_push_tokens;
create policy "push_tokens_delete_own" on public.device_push_tokens as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "push_tokens_insert_own" on public.device_push_tokens;
create policy "push_tokens_insert_own" on public.device_push_tokens as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "push_tokens_read_own" on public.device_push_tokens;
create policy "push_tokens_read_own" on public.device_push_tokens as permissive for select to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "push_tokens_update_own" on public.device_push_tokens;
create policy "push_tokens_update_own" on public.device_push_tokens as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id))
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "favorites_all" on public.favorites;
create policy "favorites_all" on public.favorites as permissive for all to public
  using ((( SELECT auth.uid() AS uid) = user_id))
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "follows_delete" on public.follows;
create policy "follows_delete" on public.follows as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = follower_id));
drop policy if exists "follows_insert" on public.follows;
create policy "follows_insert" on public.follows as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = follower_id));
drop policy if exists "follows_select" on public.follows;
create policy "follows_select" on public.follows as permissive for select to public
  using (true);
drop policy if exists "follows_update" on public.follows;
create policy "follows_update" on public.follows as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = follower_id))
  with check ((( SELECT auth.uid() AS uid) = follower_id));
drop policy if exists "messages_insert" on public.messages;
create policy "messages_insert" on public.messages as permissive for insert to public
  with check ((( SELECT auth.uid() AS uid) = sender_id));
drop policy if exists "messages_select" on public.messages;
create policy "messages_select" on public.messages as permissive for select to public
  using (((( SELECT auth.uid() AS uid) = sender_id) OR (( SELECT auth.uid() AS uid) = recipient_id)));
drop policy if exists "messages_update" on public.messages;
create policy "messages_update" on public.messages as permissive for update to public
  using ((( SELECT auth.uid() AS uid) = recipient_id));
drop policy if exists "profiles_select" on public.profiles;
create policy "profiles_select" on public.profiles as permissive for select to public
  using (true);
drop policy if exists "profiles_update" on public.profiles;
create policy "profiles_update" on public.profiles as permissive for update to public
  using ((( SELECT auth.uid() AS uid) = id));
drop policy if exists "public_profiles_delete_own" on public.public_profiles;
create policy "public_profiles_delete_own" on public.public_profiles as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "public_profiles_insert_own" on public.public_profiles;
create policy "public_profiles_insert_own" on public.public_profiles as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "public_profiles_read" on public.public_profiles;
create policy "public_profiles_read" on public.public_profiles as permissive for select to authenticated
  using (true);
drop policy if exists "public_profiles_update_own" on public.public_profiles;
create policy "public_profiles_update_own" on public.public_profiles as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id))
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "ratings_delete" on public.ratings;
create policy "ratings_delete" on public.ratings as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "ratings_insert" on public.ratings;
create policy "ratings_insert" on public.ratings as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "ratings_select" on public.ratings;
create policy "ratings_select" on public.ratings as permissive for select to public
  using (true);
drop policy if exists "ratings_update" on public.ratings;
create policy "ratings_update" on public.ratings as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id))
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "reader_library_items_delete" on public.reader_library_items;
create policy "reader_library_items_delete" on public.reader_library_items as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "reader_library_items_insert" on public.reader_library_items;
create policy "reader_library_items_insert" on public.reader_library_items as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "reader_library_items_read" on public.reader_library_items;
create policy "reader_library_items_read" on public.reader_library_items as permissive for select to authenticated
  using (((( SELECT auth.uid() AS uid) = user_id) OR ((kind = 'COLLECTION'::text) AND (is_deleted = false) AND COALESCE(((payload ->> 'public'::text))::boolean, false))));
drop policy if exists "reader_library_items_update" on public.reader_library_items;
create policy "reader_library_items_update" on public.reader_library_items as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id))
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "Users manage their own reading progress" on public.reader_progress;
create policy "Users manage their own reading progress" on public.reader_progress as permissive for all to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id))
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "Users manage their own saved books" on public.reader_saved_books;
create policy "Users manage their own saved books" on public.reader_saved_books as permissive for all to authenticated
  using ((( SELECT auth.uid() AS uid) = user_id))
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "reading_circle_members_join" on public.reading_circle_members;
create policy "reading_circle_members_join" on public.reading_circle_members as permissive for insert to authenticated
  with check (((user_id = ( SELECT auth.uid() AS uid)) AND (role = 'MEMBER'::text) AND regelo_can_join_circle(circle_id)));
drop policy if exists "reading_circle_members_leave" on public.reading_circle_members;
create policy "reading_circle_members_leave" on public.reading_circle_members as permissive for delete to authenticated
  using ((((user_id = ( SELECT auth.uid() AS uid)) AND (role <> 'OWNER'::text)) OR ((role <> 'OWNER'::text) AND (regelo_is_circle_owner(circle_id) OR regelo_is_moderator()))));
drop policy if exists "reading_circle_members_read" on public.reading_circle_members;
create policy "reading_circle_members_read" on public.reading_circle_members as permissive for select to authenticated
  using (true);
drop policy if exists "reading_circle_members_update" on public.reading_circle_members;
create policy "reading_circle_members_update" on public.reading_circle_members as permissive for update to authenticated
  using ((user_id = ( SELECT auth.uid() AS uid)))
  with check ((user_id = ( SELECT auth.uid() AS uid)));
drop policy if exists "reading_circle_posts_insert" on public.reading_circle_posts;
create policy "reading_circle_posts_insert" on public.reading_circle_posts as permissive for insert to authenticated
  with check (((author_id = ( SELECT auth.uid() AS uid)) AND (deleted_at IS NULL) AND regelo_is_circle_member(circle_id)));
drop policy if exists "reading_circle_posts_read" on public.reading_circle_posts;
create policy "reading_circle_posts_read" on public.reading_circle_posts as permissive for select to authenticated
  using ((((deleted_at IS NULL) OR (author_id = ( SELECT auth.uid() AS uid)) OR regelo_is_circle_owner(circle_id) OR regelo_is_moderator()) AND (NOT regelo_i_blocked(author_id))));
drop policy if exists "reading_circle_posts_update" on public.reading_circle_posts;
create policy "reading_circle_posts_update" on public.reading_circle_posts as permissive for update to authenticated
  using (((author_id = ( SELECT auth.uid() AS uid)) OR regelo_is_circle_owner(circle_id) OR regelo_is_moderator()))
  with check (((author_id = ( SELECT auth.uid() AS uid)) OR regelo_is_circle_owner(circle_id) OR regelo_is_moderator()));
drop policy if exists "reading_circles_delete" on public.reading_circles;
create policy "reading_circles_delete" on public.reading_circles as permissive for delete to authenticated
  using (((owner_id = ( SELECT auth.uid() AS uid)) OR regelo_is_moderator()));
drop policy if exists "reading_circles_insert" on public.reading_circles;
create policy "reading_circles_insert" on public.reading_circles as permissive for insert to authenticated
  with check ((owner_id = ( SELECT auth.uid() AS uid)));
drop policy if exists "reading_circles_read" on public.reading_circles;
create policy "reading_circles_read" on public.reading_circles as permissive for select to authenticated
  using (true);
drop policy if exists "reading_circles_update" on public.reading_circles;
create policy "reading_circles_update" on public.reading_circles as permissive for update to authenticated
  using (((owner_id = ( SELECT auth.uid() AS uid)) OR regelo_is_moderator()))
  with check (((owner_id = ( SELECT auth.uid() AS uid)) OR regelo_is_moderator()));
drop policy if exists "progress_all" on public.reading_progress;
create policy "progress_all" on public.reading_progress as permissive for all to public
  using ((( SELECT auth.uid() AS uid) = user_id))
  with check ((( SELECT auth.uid() AS uid) = user_id));
drop policy if exists "app_config_read" on public.regelo_app_config;
create policy "app_config_read" on public.regelo_app_config as permissive for select to anon,authenticated
  using (true);
drop policy if exists "official_read" on public.regelo_official_accounts;
create policy "official_read" on public.regelo_official_accounts as permissive for select to anon,authenticated
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
drop policy if exists "Moderators can update reports" on public.reports;
create policy "Moderators can update reports" on public.reports as permissive for update to public
  using (regelo_is_moderator())
  with check (regelo_is_moderator());
drop policy if exists "reports_insert" on public.reports;
create policy "reports_insert" on public.reports as permissive for insert to public
  with check ((( SELECT auth.uid() AS uid) = reporter_id));
drop policy if exists "reports_read" on public.reports;
create policy "reports_read" on public.reports as permissive for select to authenticated
  using (((( SELECT auth.uid() AS uid) = reporter_id) OR regelo_is_moderator()));
drop policy if exists "series_delete" on public.series;
create policy "series_delete" on public.series as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "series_insert" on public.series;
create policy "series_insert" on public.series as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "series_select" on public.series;
create policy "series_select" on public.series as permissive for select to public
  using (true);
drop policy if exists "series_update" on public.series;
create policy "series_update" on public.series as permissive for update to authenticated
  using ((( SELECT auth.uid() AS uid) = author_id))
  with check ((( SELECT auth.uid() AS uid) = author_id));
drop policy if exists "timeline_all" on public.timeline_events;
create policy "timeline_all" on public.timeline_events as permissive for all to public
  using ((EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = timeline_events.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))))
  with check ((EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = timeline_events.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid))))));
drop policy if exists "user_follows_delete" on public.user_follows;
create policy "user_follows_delete" on public.user_follows as permissive for delete to authenticated
  using ((( SELECT auth.uid() AS uid) = follower_id));
drop policy if exists "user_follows_insert" on public.user_follows;
create policy "user_follows_insert" on public.user_follows as permissive for insert to authenticated
  with check ((( SELECT auth.uid() AS uid) = follower_id));
drop policy if exists "user_follows_read" on public.user_follows;
create policy "user_follows_read" on public.user_follows as permissive for select to authenticated
  using (true);
drop policy if exists "writing_challenge_entries_delete" on public.writing_challenge_entries;
create policy "writing_challenge_entries_delete" on public.writing_challenge_entries as permissive for delete to authenticated
  using ((regelo_is_moderator() OR ((author_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM writing_challenges c
  WHERE ((c.id = writing_challenge_entries.challenge_id) AND (now() < c.submission_deadline)))))));
drop policy if exists "writing_challenge_entries_insert" on public.writing_challenge_entries;
create policy "writing_challenge_entries_insert" on public.writing_challenge_entries as permissive for insert to authenticated
  with check (((author_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM books b
  WHERE ((b.id = writing_challenge_entries.book_id) AND (b.author_id = ( SELECT auth.uid() AS uid)) AND (b.publish_status = 'PUBLISHED'::text)))) AND (EXISTS ( SELECT 1
   FROM writing_challenges c
  WHERE ((c.id = writing_challenge_entries.challenge_id) AND (now() >= c.starts_at) AND (now() < c.submission_deadline) AND ((c.min_words = 0) OR (( SELECT COALESCE(sum(ch.word_count), (0)::bigint) AS "coalesce"
           FROM chapters ch
          WHERE ((ch.book_id = writing_challenge_entries.book_id) AND (ch.is_draft = false))) >= c.min_words)) AND ((c.max_words = 0) OR (( SELECT COALESCE(sum(ch.word_count), (0)::bigint) AS "coalesce"
           FROM chapters ch
          WHERE ((ch.book_id = writing_challenge_entries.book_id) AND (ch.is_draft = false))) <= c.max_words)))))));
drop policy if exists "writing_challenge_entries_read" on public.writing_challenge_entries;
create policy "writing_challenge_entries_read" on public.writing_challenge_entries as permissive for select to authenticated
  using (true);
drop policy if exists "writing_challenge_votes_delete" on public.writing_challenge_votes;
create policy "writing_challenge_votes_delete" on public.writing_challenge_votes as permissive for delete to authenticated
  using (((voter_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM writing_challenges c
  WHERE ((c.id = writing_challenge_votes.challenge_id) AND (now() < c.voting_deadline))))));
drop policy if exists "writing_challenge_votes_insert" on public.writing_challenge_votes;
create policy "writing_challenge_votes_insert" on public.writing_challenge_votes as permissive for insert to authenticated
  with check (((voter_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM writing_challenges c
  WHERE ((c.id = writing_challenge_votes.challenge_id) AND (now() >= c.submission_deadline) AND (now() < c.voting_deadline)))) AND (NOT (EXISTS ( SELECT 1
   FROM writing_challenge_entries e
  WHERE ((e.challenge_id = writing_challenge_votes.challenge_id) AND (e.book_id = writing_challenge_votes.book_id) AND (e.author_id = ( SELECT auth.uid() AS uid))))))));
drop policy if exists "writing_challenge_votes_read" on public.writing_challenge_votes;
create policy "writing_challenge_votes_read" on public.writing_challenge_votes as permissive for select to authenticated
  using ((voter_id = ( SELECT auth.uid() AS uid)));
drop policy if exists "writing_challenges_delete" on public.writing_challenges;
create policy "writing_challenges_delete" on public.writing_challenges as permissive for delete to authenticated
  using (regelo_is_moderator());
drop policy if exists "writing_challenges_insert" on public.writing_challenges;
create policy "writing_challenges_insert" on public.writing_challenges as permissive for insert to authenticated
  with check ((regelo_is_moderator() AND (created_by = ( SELECT auth.uid() AS uid))));
drop policy if exists "writing_challenges_read" on public.writing_challenges;
create policy "writing_challenges_read" on public.writing_challenges as permissive for select to authenticated
  using (true);
drop policy if exists "writing_challenges_update" on public.writing_challenges;
create policy "writing_challenges_update" on public.writing_challenges as permissive for update to authenticated
  using (regelo_is_moderator())
  with check (regelo_is_moderator());
drop policy if exists "regelo_images_delete_own" on storage.objects;
create policy "regelo_images_delete_own" on storage.objects as permissive for delete to authenticated
  using (((bucket_id = ANY (ARRAY['book-covers'::text, 'chapter-images'::text, 'avatars'::text, 'profile-banners'::text])) AND ((storage.foldername(name))[1] = (auth.uid())::text)));
drop policy if exists "regelo_images_insert_own" on storage.objects;
create policy "regelo_images_insert_own" on storage.objects as permissive for insert to authenticated
  with check (((bucket_id = ANY (ARRAY['book-covers'::text, 'chapter-images'::text, 'avatars'::text, 'profile-banners'::text])) AND ((storage.foldername(name))[1] = (auth.uid())::text)));
drop policy if exists "regelo_images_select_own" on storage.objects;
create policy "regelo_images_select_own" on storage.objects as permissive for select to authenticated
  using (((bucket_id = ANY (ARRAY['book-covers'::text, 'chapter-images'::text, 'avatars'::text, 'profile-banners'::text])) AND ((storage.foldername(name))[1] = (auth.uid())::text)));
drop policy if exists "regelo_images_update_own" on storage.objects;
create policy "regelo_images_update_own" on storage.objects as permissive for update to authenticated
  using (((bucket_id = ANY (ARRAY['book-covers'::text, 'chapter-images'::text, 'avatars'::text, 'profile-banners'::text])) AND ((storage.foldername(name))[1] = (auth.uid())::text)))
  with check (((bucket_id = ANY (ARRAY['book-covers'::text, 'chapter-images'::text, 'avatars'::text, 'profile-banners'::text])) AND ((storage.foldername(name))[1] = (auth.uid())::text)));

-- ===================== TÁBLAJOGOK (anon / authenticated) =====================
-- app_error_reports: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- app_error_reports: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- author_announcements: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- author_announcements: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- blocked_users: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- blocked_users: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- book_likes: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- book_likes: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- book_rating_stats: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- book_rating_stats: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- book_ratings: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- book_ratings: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- book_views: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- book_views: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- books: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- books: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- chapter_comments: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- chapter_comments: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- chapter_like_stats: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- chapter_like_stats: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- chapter_likes: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- chapter_likes: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- chapters: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- chapters: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- characters: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- characters: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- comments: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- comments: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- community_messages: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE
-- community_messages: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE
-- community_moderators: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- community_moderators: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- community_notifications: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE
-- community_notifications: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE
-- community_reports: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- community_reports: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- device_push_tokens: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- device_push_tokens: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- favorites: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- favorites: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- follows: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- follows: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- messages: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- messages: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- profiles: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- profiles: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- public_profiles: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- public_profiles: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- ratings: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- ratings: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reader_library_items: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reader_library_items: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reader_progress: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reader_progress: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reader_saved_books: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reader_saved_books: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reading_circle_members: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reading_circle_members: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE
-- reading_circle_posts: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reading_circle_posts: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE
-- reading_circles: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reading_circles: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE
-- reading_progress: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reading_progress: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_app_config: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_app_config: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_official_accounts: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_official_accounts: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_official_books: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_official_books: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_official_chapter_sources: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_official_chapter_sources: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_official_discussion_prompts: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_official_discussion_prompts: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_official_subscriptions: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_official_subscriptions: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_rate_events: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_rate_events: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_roles: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- regelo_roles: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reports: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- reports: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- series: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- series: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- timeline_events: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- timeline_events: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- user_follows: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- user_follows: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- writing_challenge_entries: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- writing_challenge_entries: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE
-- writing_challenge_votes: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- writing_challenge_votes: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE
-- writing_challenges: anon: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- writing_challenges: authenticated: DELETE, INSERT, REFERENCES, SELECT, TRIGGER, TRUNCATE, UPDATE
-- Oszlopszintű jogok:
-- community_messages: authenticated: UPDATE (read_at)
-- community_notifications: authenticated: UPDATE (read_at)
-- reading_circle_members: authenticated: UPDATE (muted)
-- reading_circle_posts: authenticated: UPDATE (body, deleted_at)
-- reading_circles: authenticated: UPDATE (book_id, description, name)

-- ===================== CRON =====================
select cron.schedule('regelo-publish-scheduled', '*/10 * * * *', $cron$select public.regelo_publish_scheduled()$cron$);
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

-- Realtime publikáció: community_messages, community_notifications
-- Storage tárolók: [{"id":"avatars","public":true,"file_size_limit":5242880,"allowed_mime_types":["image/jpeg","image/png","image/webp"]},{"id":"book-covers","public":true,"file_size_limit":5242880,"allowed_mime_types":["image/jpeg","image/png","image/webp"]},{"id":"chapter-images","public":true,"file_size_limit":10485760,"allowed_mime_types":["image/jpeg","image/png","image/webp"]},{"id":"profile-banners","public":true,"file_size_limit":5242880,"allowed_mime_types":["image/jpeg","image/png","image/webp"]}]
