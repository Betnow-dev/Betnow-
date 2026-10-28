create table if not exists public.app_projects (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(name) between 2 and 60),
    package_name text not null unique check (
        package_name ~ '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*){1,}$'
    ),
    site_url text not null check (site_url ~ '^https://'),
    admob_app_id text not null,
    admob_banner_id text not null,
    status_bar_color text not null default '#E91D31'
        check (status_bar_color ~ '^#[0-9A-Fa-f]{6}$'),
    created_by uuid not null default auth.uid() references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.app_builds (
    id uuid primary key default gen_random_uuid(),
    app_id uuid not null references public.app_projects(id) on delete cascade,
    requested_by uuid not null default auth.uid() references auth.users(id),
    status text not null default 'queued'
        check (status in ('queued', 'running', 'succeeded', 'failed')),
    workflow_run_id bigint,
    apk_object_path text,
    aab_object_path text,
    error_message text,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz
);

create index if not exists app_builds_app_created_idx
    on public.app_builds (app_id, created_at desc);

alter table public.app_projects enable row level security;
alter table public.app_builds enable row level security;

grant select, insert, update, delete on public.app_projects to authenticated;
grant select, insert on public.app_builds to authenticated;

drop policy if exists "Admins can manage app projects" on public.app_projects;
create policy "Admins can manage app projects"
    on public.app_projects for all
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

drop policy if exists "Admins can read app builds" on public.app_builds;
create policy "Admins can read app builds"
    on public.app_builds for select
    to authenticated
    using (
        exists (
            select 1
            from public.app_admins
            where user_id = auth.uid()
        )
    );

drop policy if exists "Admins can request app builds" on public.app_builds;
create policy "Admins can request app builds"
    on public.app_builds for insert
    to authenticated
    with check (
        requested_by = auth.uid()
        and status = 'queued'
        and exists (
            select 1
            from public.app_admins
            where user_id = auth.uid()
        )
    );

insert into storage.buckets (id, name, public)
values ('app-builds', 'app-builds', false)
on conflict (id) do nothing;

grant select on storage.objects to authenticated;

drop policy if exists "Admins can download app build artifacts" on storage.objects;
create policy "Admins can download app build artifacts"
    on storage.objects for select
    to authenticated
    using (
        bucket_id = 'app-builds'
        and exists (
            select 1
            from public.app_admins
            where user_id = auth.uid()
        )
    );
