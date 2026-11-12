-- PromoCode backend schema for Supabase (PostgreSQL).
-- Run once in the Supabase SQL editor. Safe to re-run.
--
-- Sync contract (see app/src/main/java/com/promocodeapp/sync/SyncEngine.kt):
--   * ids are UUIDs generated on the device, so a row has one identity everywhere;
--   * user_id is filled from the caller's JWT and row-level security confines
--     every user to their own rows; the client never sends it;
--   * an upsert only replaces a row when its updated_at is newer (last write wins);
--   * server_updated_at is set by the database on every accepted write and is
--     the cursor devices use to pull changes;
--   * deletions are soft (deleted_at) so every device learns about them.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- tables

create table if not exists public.places (
    id                uuid primary key,
    user_id           uuid not null default auth.uid() references auth.users (id) on delete cascade,
    name              text not null check (length(name) between 1 and 200),
    address           text,
    latitude          double precision not null check (latitude between -90 and 90),
    longitude         double precision not null check (longitude between -180 and 180),
    radius_meters     integer not null default 150 check (radius_meters between 100 and 2000),
    created_at        bigint not null,
    updated_at        bigint not null,
    deleted_at        bigint,
    server_updated_at timestamptz not null default clock_timestamp()
);

create table if not exists public.coupons (
    id                uuid primary key,
    user_id           uuid not null default auth.uid() references auth.users (id) on delete cascade,
    code              text not null default '',
    merchant          text not null check (length(merchant) between 1 and 200),
    discount_type     text not null check (discount_type in ('PERCENTAGE', 'FIXED_AMOUNT', 'BOGO', 'FREE_SHIPPING')),
    discount_value    double precision not null default 0 check (discount_value >= 0),
    currency          text not null default 'USD',
    min_purchase      double precision check (min_purchase >= 0),
    category          text,
    notes             text,
    expires_on        date,
    barcode           text,
    barcode_format    text,
    is_favorite       boolean not null default false,
    used_at           bigint,
    is_archived       boolean not null default false,
    place_ids         uuid[] not null default '{}',
    created_at        bigint not null,
    updated_at        bigint not null,
    deleted_at        bigint,
    server_updated_at timestamptz not null default clock_timestamp()
);

create table if not exists public.memberships (
    id                uuid primary key,
    user_id           uuid not null default auth.uid() references auth.users (id) on delete cascade,
    organization      text not null check (length(organization) between 1 and 200),
    member_number     text not null default '',
    type              text not null default 'OTHER',
    started_on        date,
    renews_on         date,
    fee               double precision check (fee >= 0),
    billing_cycle     text not null default 'NONE' check (billing_cycle in ('NONE', 'MONTHLY', 'YEARLY')),
    currency          text not null default 'USD',
    notes             text,
    reminder_days     integer not null default 7 check (reminder_days between 0 and 365),
    is_active         boolean not null default true,
    place_ids         uuid[] not null default '{}',
    created_at        bigint not null,
    updated_at        bigint not null,
    deleted_at        bigint,
    server_updated_at timestamptz not null default clock_timestamp()
);

-- FCM tokens, for optional server-sent pushes (e.g. product announcements).
create table if not exists public.device_tokens (
    token       text primary key,
    user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
    platform    text not null default 'android',
    updated_at  timestamptz not null default now()
);

create index if not exists places_sync_idx      on public.places (user_id, server_updated_at);
create index if not exists coupons_sync_idx     on public.coupons (user_id, server_updated_at);
create index if not exists memberships_sync_idx on public.memberships (user_id, server_updated_at);

-- ---------------------------------------------------------------- write rules

-- Runs before every insert/update on the synced tables:
--   * pins user_id to the caller (an upsert can never move a row to another user);
--   * ignores updates older than what is stored (last write wins);
--   * stamps server_updated_at, which must strictly increase per write.
create or replace function public.sync_write_guard() returns trigger
language plpgsql security invoker as $$
begin
    new.user_id := coalesce(auth.uid(), new.user_id);
    if tg_op = 'UPDATE' then
        if new.user_id <> old.user_id then
            raise exception 'row belongs to another user' using errcode = '42501';
        end if;
        if new.updated_at < old.updated_at then
            return null; -- stale write: keep the newer row, report success
        end if;
    end if;
    new.server_updated_at := clock_timestamp();
    return new;
end $$;

do $$
declare t text;
begin
    foreach t in array array['places', 'coupons', 'memberships'] loop
        execute format('drop trigger if exists sync_write_guard on public.%I', t);
        execute format('create trigger sync_write_guard before insert or update on public.%I
                        for each row execute function public.sync_write_guard()', t);
    end loop;
end $$;

-- Tombstones older than 90 days are no longer needed by any device that syncs
-- at least that often. Schedule with pg_cron if desired:
--   select cron.schedule('purge-tombstones', '0 4 * * *', 'select public.purge_tombstones()');
create or replace function public.purge_tombstones() returns void
language sql security definer as $$
    delete from public.places      where deleted_at is not null and server_updated_at < now() - interval '90 days';
    delete from public.coupons     where deleted_at is not null and server_updated_at < now() - interval '90 days';
    delete from public.memberships where deleted_at is not null and server_updated_at < now() - interval '90 days';
$$;

-- ---------------------------------------------------------------- row-level security

alter table public.places        enable row level security;
alter table public.coupons       enable row level security;
alter table public.memberships   enable row level security;
alter table public.device_tokens enable row level security;

do $$
declare t text;
begin
    foreach t in array array['places', 'coupons', 'memberships', 'device_tokens'] loop
        execute format('drop policy if exists "own rows" on public.%I', t);
        execute format('create policy "own rows" on public.%I for all to authenticated
                        using (user_id = auth.uid()) with check (user_id = auth.uid())', t);
    end loop;
end $$;

-- Anonymous callers get nothing; signed-in users only reach their own rows.
revoke all on public.places, public.coupons, public.memberships, public.device_tokens from anon;
grant select, insert, update on public.places, public.coupons, public.memberships to authenticated;
grant select, insert, update, delete on public.device_tokens to authenticated;
