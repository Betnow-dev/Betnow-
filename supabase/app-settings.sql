create table if not exists public.app_settings (
	id integer primary key check (id = 1),
	site_url text not null check (site_url ~ '^https://'),
	admob_banner_id text not null,
	status_bar_color text not null default '#E91D31'
		check (status_bar_color ~ '^#[0-9A-Fa-f]{6}$'),
	updated_at timestamptz not null default now()
);

create table if not exists public.app_admins (
	user_id uuid primary key references auth.users(id) on delete cascade
);

alter table public.app_settings enable row level security;
alter table public.app_admins enable row level security;

grant select on public.app_settings to anon, authenticated;
grant update on public.app_settings to authenticated;
grant select on public.app_admins to authenticated;

drop policy if exists "Anyone can read app settings" on public.app_settings;
create policy "Anyone can read app settings"
	on public.app_settings for select
	to anon, authenticated
	using (true);

drop policy if exists "Admins can update app settings" on public.app_settings;
create policy "Admins can update app settings"
	on public.app_settings for update
	to authenticated
	using (
		exists (
			select 1
			from public.app_admins
			where user_id = auth.uid()
		)
	)
	with check (
		exists (
			select 1
			from public.app_admins
			where user_id = auth.uid()
		)
	);

drop policy if exists "Admins can read their own access row" on public.app_admins;
create policy "Admins can read their own access row"
	on public.app_admins for select
	to authenticated
	using (user_id = auth.uid());

insert into public.app_settings (
	id,
	site_url,
	admob_banner_id,
	status_bar_color
)
values (
	1,
	'https://betnow-rewards-five.vercel.app/',
	'ca-app-pub-3940256099942544/9214589741',
	'#E91D31'
)
on conflict (id) do nothing;
