# Shared hosted demo storage

Vercel may route consecutive requests to different containers. H2 is suitable for local offline demonstrations but cannot share records between those containers. The optional hosted adapter keeps the same Java repository interface and persists two small JSON datasets through a protected Supabase Edge Function.

## Configuration

1. Apply `schema.sql` to the project dedicated to ScholarAI. It creates only `scholarai_demo_state`; existing application tables remain separate. RLS blocks client access. Only the Edge Function's built-in service credential accesses it.
2. Generate a cryptographically random secret of at least 32 bytes. Configure its SHA-256 digest as `SCHOLARAI_DEMO_STORAGE_KEY_SHA256` in the Edge Function environment. Configure the original secret as `DEMO_STORAGE_KEY` in Vercel's server environment. Never commit either the secret or a service-role key.
3. Deploy `index.ts` as `scholarai-demo-store` with JWT verification disabled: the function validates its own private server key on every call. Configure `DEMO_STORAGE_URL` in Vercel as the function's HTTPS URL.
4. Redeploy and test saving from one request and reading from another. A write uses a version comparison and retries conflicts so multiple containers cannot silently overwrite each other's updates.

If the hosted connection fails, Java reports a retryable error rather than writing into an isolated temporary database. Offline local mode remains H2 when the shared-storage variables are absent.

Uploaded files remain temporary private files on each demo container; the verified document record persists in shared storage. The UI shows verification records and does not offer a file-download feature. Use sample documents only. For durable original files and private real accounts, configure the existing Supabase Storage adapter.
