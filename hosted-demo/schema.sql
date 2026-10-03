-- Isolated sample-data table. No anonymous or authenticated client access.
create table if not exists public.scholarai_demo_state (
  dataset text primary key check (dataset in ('scholarships', 'financial-aid')),
  version bigint not null default 1,
  payload jsonb not null check (jsonb_typeof(payload) = 'object'),
  updated_at timestamptz not null default now()
);
alter table public.scholarai_demo_state enable row level security;
revoke all on public.scholarai_demo_state from anon, authenticated;
-- Only the private Edge Function uses its built-in service credential.
