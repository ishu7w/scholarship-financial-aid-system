# Running and deploying

## Local demo

Use JDK 17+, Maven and Node.js 22:

```bash
npm ci
npm run dev
```

The launcher generates a private `.env.local` signing key, builds Java, starts it on loopback port 8080, and starts Next.js on port 3000. Data lives in the ignored `backend/data/demo/` directory. Both services stop when the launcher stops.

For a production-style local run:

```bash
npm run build:java
npm run build
npm start
npm run verify:deploy
```

## Existing Vercel classroom demo

`vercel.json` uses `Dockerfile.vercel`. The container builds Java and the Next.js standalone frontend, then `scripts/demo-container.mjs` starts Java before accepting web requests. Only the web server is public. The image deliberately runs the original no-account demo persona.

The rebuild retains that configuration. The Java platform and financial-aid databases are stored under `/tmp/scholarai-demo` in the container. This is **temporary demo storage**: data may reset on redeployment, container replacement or scaling. Local H2 files survive ordinary local restarts; a server intended to retain data needs a persistent disk and one Java process for that dataset.

A frontend-only deployment cannot execute these Java modules. Keep the supplied combined-container deployment or run the Java service alongside Next.js on a Java-capable host.

## Optional authenticated mode

Set `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY` in `.env.local`. Supabase verifies credentials and issues session cookies. Java stores the account role, disabled state and academic profile. Public registration accepts student and institution accounts only; it cannot create administrators.

`SUPABASE_SERVICE_ROLE_KEY` enables the existing private Supabase document bucket and direct registration flow. The Next.js server extracts PDF text; Java checks the content against the owner's stored profile. Without a storage provider, the original demo upload-unavailable behaviour remains. Pasted resume text still works.

`ANTHROPIC_API_KEY` optionally enables the external copilot adapter. The fallback answers come from Java.

The old `DATABASE_URL`, Drizzle migration and Postgres seeding commands are removed. Existing Postgres installations require an explicit data migration before switching to this branch; records are not copied automatically. The classroom Vercel demo has no hosted Postgres database.

## Java settings

| Variable | Meaning |
| --- | --- |
| `AID_API_SECRET` | Shared server-only signing secret; minimum 32 characters |
| `AID_PORT` | Java listener port; default 8080 |
| `AID_API_URL` | Next.js-to-Java address; launcher sets the matching loopback URL |
| `AID_DATA_DIR` | H2 storage directory |
| `AID_ALLOW_DEMO` | Whether Java accepts signed demo identities; launcher chooses this from the auth configuration |

The `AID_` names remain compatible with the existing deployment even though Java now serves the whole application's rules.
