# Architecture

```text
Browser: existing React components + animations
   ↓ forms / server actions / page requests
Next.js: session cookies, input transport, PDF extraction, optional integrations
   ↓ signed HTTP on loopback
Java: PlatformApi + financial-aid routes
   ↓ feature services
Java: JDBC repositories
   ↓
H2 files in AID_DATA_DIR
```

Java owns scholarship matching, scoring, profiles, applications, decisions, financial aid, resume analysis, document verification, copilot rules, administration and notifications. Next.js owns rendering, browser interaction and external-provider adapters. Client-side search/filtering is a presentation concern and stays in the frontend.

The Java API is not exposed directly to the browser. Next.js signs the method, path, timestamp, nonce, caller identity and body digest. Java checks the signature and replay window before invoking a service. Services check ownership and roles again for their operations.

`PlatformRepository` is the storage abstraction. `JdbcPlatformRepository` implements transactional persistence for the small classroom dataset; financial aid retains `AidRepository` and `JdbcAidRepository`. Database initialization seeds the original dataset once.

The frontend has one `JavaDataSource`. Server actions forward writes to Java and revalidate affected pages. The frontend engine file is a thin asynchronous adapter; it contains no scoring implementation. `engine-contracts.ts` contains response types and display labels only.

Dashboard updates poll the Java-backed server pages every 30 seconds while visible and refresh on window focus. Notifications retain their existing 30-second polling behaviour. No Postgres realtime subscription is needed.

See [README.md](README.md) for module locations and [OOP_GUIDE.md](OOP_GUIDE.md) for class relationships and request examples.
