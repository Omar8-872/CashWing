-- CashWing database for Supabase.
-- Paste ALL of this in Supabase -> SQL Editor -> New query -> Run. It is safe to run again.
--
-- Security idea: the app can NOT read or change the tables. It can only call the functions below.
-- The functions check the PIN / session, count wrong attempts, and move the money inside one
-- database transaction, so a modified app cannot cheat.

create extension if not exists pgcrypto with schema extensions;

-- ======================= tables =======================

create table if not exists public.users (
    phone        text primary key,
    name         text not null,
    balance      numeric(14,2) not null default 20000 check (balance >= 0),
    active       boolean not null default true,
    pin_hash     text,                         -- bcrypt hash, never the PIN itself (null = cannot log in)
    failed       int not null default 0,       -- wrong PINs in a row
    locked_until timestamptz,
    created_at   timestamptz not null default now()
);

create table if not exists public.sessions (
    token      uuid primary key default gen_random_uuid(),
    phone      text not null references public.users(phone) on delete cascade,
    created_at timestamptz not null default now()
);
create index if not exists sessions_phone_idx on public.sessions(phone);

create table if not exists public.transactions (
    id       bigserial primary key,
    ts       bigint not null unique,           -- time in milliseconds, also used as id in the app
    sender   text not null references public.users(phone),
    receiver text not null references public.users(phone),
    amount   numeric(14,2) not null check (amount > 0),
    status   text not null check (status in ('Completed', 'Rejected'))
);
create index if not exists transactions_sender_idx on public.transactions(sender);
create index if not exists transactions_receiver_idx on public.transactions(receiver);

-- the charities (same phones as Charities.java in the app); they cannot log in (pin_hash is null)
insert into public.users (phone, name, balance, active) values
    ('00000000000', 'Charity Fund', 0, true),
    ('00000000001', 'Hope Children''s Hospital', 0, true),
    ('00000000002', 'Heart Care Fund', 0, true),
    ('00000000003', 'Cancer Patients Support', 0, true),
    ('00000000004', 'Orphans Care Home', 0, true),
    ('00000000005', 'Orphan Sponsorship', 0, true),
    ('00000000006', 'Food for the Needy', 0, true),
    ('00000000007', 'Clean Water', 0, true),
    ('00000000008', 'Education Support', 0, true),
    ('00000000009', 'Elderly Care', 0, true)
on conflict (phone) do nothing;

-- ======================= lock the tables =======================

alter table public.users enable row level security;         -- no policy = nobody can use the table directly
alter table public.sessions enable row level security;
alter table public.transactions enable row level security;
revoke all on public.users, public.sessions, public.transactions from anon, authenticated;
revoke all on sequence public.transactions_id_seq from anon, authenticated;

-- ======================= private helpers (the app cannot call these) =======================

create or replace function public._session_ok(p_phone text, p_token text)
returns boolean
language plpgsql security definer set search_path = public, extensions
as $$
begin
    return exists (
        select 1
        from sessions s join users u on u.phone = s.phone
        where s.token = p_token::uuid
          and s.phone = p_phone
          and u.active
          and s.created_at > now() - interval '30 days'
    );
exception when others then
    return false;
end;
$$;

-- checks a PIN and counts wrong attempts: 3 wrong in a row lock the account for 5 minutes
create or replace function public._check_pin(p_phone text, p_pin text)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
declare
    u users%rowtype;
    v_failed int;
begin
    select * into u from users where phone = p_phone and active and pin_hash is not null for update;
    if not found then
        return jsonb_build_object('result', 'NOT_FOUND');
    end if;
    if u.locked_until is not null and u.locked_until > now() then
        return jsonb_build_object('result', 'LOCKED');
    end if;
    if u.pin_hash = extensions.crypt(coalesce(p_pin, ''), u.pin_hash) then
        update users set failed = 0, locked_until = null where phone = p_phone;
        return jsonb_build_object('result', 'OK');
    end if;
    v_failed := u.failed + 1;
    if v_failed >= 3 then
        update users set failed = 0, locked_until = now() + interval '5 minutes' where phone = p_phone;
        return jsonb_build_object('result', 'LOCKED');
    end if;
    update users set failed = v_failed where phone = p_phone;
    return jsonb_build_object('result', 'WRONG', 'left', 3 - v_failed);
end;
$$;

-- a time in milliseconds that is never used twice (call it only while holding the transfer lock)
create or replace function public._next_ts()
returns bigint
language sql security definer set search_path = public, extensions
as $$
    select greatest((extract(epoch from clock_timestamp()) * 1000)::bigint,
                    coalesce(max(ts), 0) + 1)
    from transactions;
$$;

revoke execute on function public._session_ok(text, text) from public, anon, authenticated;
revoke execute on function public._check_pin(text, text) from public, anon, authenticated;
revoke execute on function public._next_ts() from public, anon, authenticated;

-- ======================= account =======================

create or replace function public.sign_up(p_name text, p_phone text, p_pin text)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
declare
    v_name text := btrim(coalesce(p_name, ''));
begin
    if p_phone is null or p_phone !~ '^01[0-9]{9}$' then
        return jsonb_build_object('error', 'BAD_PHONE');
    end if;
    if p_pin is null or p_pin !~ '^[0-9]{8,12}$' then
        return jsonb_build_object('error', 'BAD_PIN');
    end if;
    if char_length(v_name) < 1 or char_length(v_name) > 60 then
        return jsonb_build_object('error', 'BAD_NAME');
    end if;
    begin
        insert into users (phone, name, pin_hash)
        values (p_phone, v_name, extensions.crypt(p_pin, extensions.gen_salt('bf')));
    exception when unique_violation then
        return jsonb_build_object('error', 'TAKEN');   -- the phone was used before, even by a deleted account
    end;
    return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.sign_in(p_phone text, p_pin text)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
declare
    r jsonb;
    v_user users%rowtype;
    v_token uuid;
begin
    r := _check_pin(p_phone, p_pin);
    if r->>'result' <> 'OK' then
        return (r - 'result') || jsonb_build_object('error', r->>'result');
    end if;
    delete from sessions where created_at < now() - interval '30 days';
    insert into sessions (phone) values (p_phone) returning token into v_token;
    select * into v_user from users where phone = p_phone;
    return jsonb_build_object('ok', true, 'token', v_token, 'name', v_user.name, 'balance', v_user.balance);
end;
$$;

create or replace function public.sign_out(p_phone text, p_token text)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
begin
    begin
        delete from sessions where phone = p_phone and token = p_token::uuid;
    exception when others then
        null;
    end;
    return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.check_pin(p_phone text, p_token text, p_pin text)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
declare
    r jsonb;
begin
    if not _session_ok(p_phone, p_token) then
        return jsonb_build_object('error', 'AUTH');
    end if;
    r := _check_pin(p_phone, p_pin);
    if r->>'result' = 'OK' then
        return jsonb_build_object('ok', true);
    end if;
    return (r - 'result') || jsonb_build_object('error', r->>'result');
end;
$$;

create or replace function public.change_pin(p_phone text, p_token text, p_old_pin text, p_new_pin text)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
declare
    r jsonb;
begin
    if not _session_ok(p_phone, p_token) then
        return jsonb_build_object('error', 'AUTH');
    end if;
    if p_new_pin is null or p_new_pin !~ '^[0-9]{8,12}$' then
        return jsonb_build_object('error', 'BAD_PIN');
    end if;
    r := _check_pin(p_phone, p_old_pin);
    if r->>'result' <> 'OK' then
        return (r - 'result') || jsonb_build_object('error', r->>'result');
    end if;
    update users set pin_hash = extensions.crypt(p_new_pin, extensions.gen_salt('bf')) where phone = p_phone;
    return jsonb_build_object('ok', true);
end;
$$;

-- the account is switched off; its transactions stay so the other users keep a correct history
create or replace function public.delete_account(p_phone text, p_token text, p_pin text)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
declare
    r jsonb;
begin
    if not _session_ok(p_phone, p_token) then
        return jsonb_build_object('error', 'AUTH');
    end if;
    r := _check_pin(p_phone, p_pin);
    if r->>'result' <> 'OK' then
        return (r - 'result') || jsonb_build_object('error', r->>'result');
    end if;
    update users set active = false, balance = 0, pin_hash = null where phone = p_phone;
    delete from sessions where phone = p_phone;
    return jsonb_build_object('ok', true);
end;
$$;

-- ======================= data for the app =======================

-- my balance + my transactions + the names of the people in them, in one call
create or replace function public.get_state(p_phone text, p_token text)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
declare
    v_user users%rowtype;
    v_txs jsonb;
    v_people jsonb;
begin
    if not _session_ok(p_phone, p_token) then
        return jsonb_build_object('error', 'AUTH');
    end if;
    select * into v_user from users where phone = p_phone;

    select coalesce(jsonb_agg(jsonb_build_object(
               'ts', t.ts, 'sender', t.sender, 'receiver', t.receiver,
               'amount', t.amount, 'status', t.status) order by t.ts desc), '[]'::jsonb)
    into v_txs
    from (select * from transactions
          where sender = p_phone or receiver = p_phone
          order by ts desc limit 500) t;

    select coalesce(jsonb_agg(jsonb_build_object('phone', u.phone, 'name', u.name, 'active', u.active)), '[]'::jsonb)
    into v_people
    from users u
    where u.phone <> p_phone
      and exists (select 1 from transactions t
                  where (t.sender = p_phone and t.receiver = u.phone)
                     or (t.receiver = p_phone and t.sender = u.phone));

    return jsonb_build_object('ok', true, 'name', v_user.name, 'balance', v_user.balance,
                              'active', v_user.active, 'transactions', v_txs, 'people', v_people);
end;
$$;

create or replace function public.check_receiver(p_phone text, p_token text, p_receiver text)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
declare
    v_name text;
begin
    if not _session_ok(p_phone, p_token) then
        return jsonb_build_object('error', 'AUTH');
    end if;
    select name into v_name from users where phone = p_receiver and active;
    if not found then
        return jsonb_build_object('error', 'NOT_FOUND');
    end if;
    return jsonb_build_object('ok', true, 'name', v_name);
end;
$$;

-- ======================= money =======================

-- all the steps happen together or none of them happens
create or replace function public.transfer(p_phone text, p_token text, p_receiver text, p_amount numeric)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
declare
    v_amount numeric(14,2);
    v_sender users%rowtype;
    v_receiver users%rowtype;
    v_ts bigint;
    v_new numeric(14,2);
begin
    if not _session_ok(p_phone, p_token) then
        return jsonb_build_object('error', 'AUTH');
    end if;
    if p_amount is null or p_amount <= 0 or p_amount > 1000000 then
        return jsonb_build_object('error', 'BAD_AMOUNT');
    end if;
    v_amount := round(p_amount, 2);
    if v_amount <= 0 then
        return jsonb_build_object('error', 'BAD_AMOUNT');
    end if;
    if p_phone = p_receiver then
        return jsonb_build_object('error', 'SELF');
    end if;

    perform pg_advisory_xact_lock(7001);   -- one transfer at a time: no double spending, no deadlock

    select * into v_sender from users where phone = p_phone and active for update;
    if not found then
        return jsonb_build_object('error', 'NO_SENDER');
    end if;
    select * into v_receiver from users where phone = p_receiver and active for update;
    if not found then
        return jsonb_build_object('error', 'NO_RECEIVER');
    end if;

    v_ts := _next_ts();
    if v_sender.balance < v_amount then
        insert into transactions (ts, sender, receiver, amount, status)
        values (v_ts, p_phone, p_receiver, v_amount, 'Rejected');
        return jsonb_build_object('error', 'NO_MONEY', 'ts', v_ts);
    end if;

    update users set balance = balance - v_amount where phone = p_phone returning balance into v_new;
    update users set balance = balance + v_amount where phone = p_receiver;
    insert into transactions (ts, sender, receiver, amount, status)
    values (v_ts, p_phone, p_receiver, v_amount, 'Completed');

    return jsonb_build_object('ok', true, 'balance', v_new, 'ts', v_ts);
end;
$$;

-- a transfer that was refused (not enough money) is still saved in the history
create or replace function public.record_rejected(p_phone text, p_token text, p_receiver text, p_amount numeric)
returns jsonb
language plpgsql security definer set search_path = public, extensions
as $$
declare
    v_ts bigint;
    v_amount numeric(14,2);
begin
    if not _session_ok(p_phone, p_token) then
        return jsonb_build_object('error', 'AUTH');
    end if;
    if p_amount is null or p_amount <= 0 or p_amount > 1000000 or p_phone = p_receiver then
        return jsonb_build_object('error', 'BAD_AMOUNT');
    end if;
    v_amount := round(p_amount, 2);
    if v_amount <= 0 then
        return jsonb_build_object('error', 'BAD_AMOUNT');
    end if;
    if not exists (select 1 from users where phone = p_receiver and active) then
        return jsonb_build_object('error', 'NO_RECEIVER');
    end if;
    perform pg_advisory_xact_lock(7001);
    v_ts := _next_ts();
    insert into transactions (ts, sender, receiver, amount, status)
    values (v_ts, p_phone, p_receiver, v_amount, 'Rejected');
    return jsonb_build_object('ok', true, 'ts', v_ts);
end;
$$;

-- make the new functions visible to the API right now
notify pgrst, 'reload schema';
