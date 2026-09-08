# Run ScholarAI with the Java financial aid backend

## Prerequisites

- Node.js 22.23.2 with npm 10.9.8 (matching `.nvmrc` and GitHub CI)
- JDK 17+ (Java 21 LTS is a suitable choice)
- Maven 3.9+

Regenerate the dependency lockfile using this Node/npm version. A lockfile produced by npm 11 can resolve nested optional esbuild dependencies differently and be rejected by npm 10 in CI.

The browser UI remains the existing Next.js app. Financial aid rules and persistence run in a separate Java process.

```sh
nvm use              # when using nvm; run nvm install first if needed
npm ci
npm run dev
```

The launcher builds the Java JAR, starts Java on `127.0.0.1:8080`, waits for its health endpoint, and starts Next.js on port 3000. Stop both with Ctrl+C. The first Maven build needs internet access to download dependencies.

Open http://localhost:3000 for the original landing page. Financial Aid is in the dashboard sidebar, or directly at http://localhost:3000/financial-aid.

The launcher generates a private `AID_API_SECRET` in ignored `.env.local` if one is not already configured. Never commit this file. No credentials are printed by the launcher.

## Persistence

Financial aid uses H2 through JDBC. The local database is saved under `backend/data/demo/financial-aid.mv.db` and survives full process restarts. This file is ignored by Git. Demo users share the original Aarya persona, so demo mode is intended for local classroom use.

When Supabase live authentication is configured, the launcher disables demo review and uses `backend/data/live/` instead. The Java service filters student records and enforces admin review permission. Institutions retain their scholarship workflow and cannot review financial aid.

No extra Supabase migration is needed for this Java aid module. The original scholarship database and original migrations remain unchanged.

## Environment variables

| Variable | Purpose |
| --- | --- |
| `AID_API_SECRET` | Same private HMAC credential in Next.js and Java; at least 32 characters. Generated locally by the launcher. |
| `AID_PORT` | Local Java port; defaults to `8080`. |
| `AID_DATA_DIR` | Optional absolute database directory; defaults to separate demo/live directories. |
| `AID_API_URL` | Used by the Next.js adapter when services are run separately; defaults to `http://127.0.0.1:8080`. The combined launcher sets it from `AID_PORT`. |
| `AID_ALLOW_DEMO` | Java-only switch; defaults to false for standalone Java. The local launcher enables it only without live Supabase auth. |

The existing `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`, and `DATABASE_URL` configure original ScholarAI auth/data features as described in the upstream documentation.

## Commands

```sh
npm run dev          # Java + Next.js development servers
npm run typecheck    # Generate Next.js route types, then TypeScript check
npm test             # Existing frontend/engine tests + adapter tests
npm run test:java    # Java domain, persistence, authorization, HTTP tests
npm run lint
npm run build:java   # Test and package executable Java JAR
npm run build        # Build the Next.js frontend
npm start            # Java + production Next.js (run both builds first)
```

`npm run dev:web` is available if Java is already running separately. It does not start Java.

## Java API

Every `/api/aid/*` request must carry the server-generated signed identity headers. Calling Java directly from the browser is intentionally unsupported.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/health` | Public local liveness check |
| GET | `/api/aid/programs` | Program metadata from Java subclasses |
| POST | `/api/aid/assess` | Funding gap and program eligibility |
| GET | `/api/aid/applications` | Own records, or all records for admins |
| POST | `/api/aid/applications` | Validated student submission |
| POST | `/api/aid/applications/{id}/transition` | Version-checked withdrawal, review, decision, or disbursement record |

## Deployment boundary

The combined launcher runs Java and Next.js on one machine. Java binds to loopback. A frontend-only Vercel deployment cannot run this Java JAR or keep its H2 file; production deployment requires a host/container with both processes and a persistent disk, or a separately secured Java service with appropriate network configuration. Live auth and original external integrations were not tested with user credentials.

This is a separate project with independent Git history. The source repository `../ScholarAI` remains untouched. The new GitHub repository is `ishu7w/scholarship-financial-aid-system`; never configure the original ScholarAI repository as a push target.
