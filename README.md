# Scholarship Management and Financial Aid System

A Java OOP college project with the existing ScholarAI web interface. The layouts, colours, components and animations remain in the Next.js frontend. Java owns the scholarship rules, scoring, student profiles, applications, institution decisions, financial aid, document verification, notifications and administration.

## Run the project

Install **JDK 17 or newer**, **Maven**, and **Node.js 22**. Then:

```bash
npm ci
npm run dev
```

Open **http://localhost:3000**. The command builds and starts Java on port 8080, waits for it to become ready, and starts the frontend. It creates a private signing key in the ignored `.env.local` file automatically. No hosted database is needed for the classroom demo.

The original demo persona is Aarya Sharma. Demo login and registration keep their existing simulated behaviour. The existing role guards and read-only institution/admin demo controls are retained. For authenticated accounts, Supabase still handles passwords and cookies; Java stores profiles and roles.

## Module structure

```text
backend/src/main/java/com/scholarai/
  aid/            Financial-aid programs, eligibility and payment lifecycle
  account/        Account profiles and roles
  student/        Academic profiles, validation and improvement roadmaps
  scholarship/    Scholarship model, eligibility and matching
  scoring/        Weighted profile scores and fraud signals
  application/    Apply, withdraw, save and freeze application scores
  institution/    Publish programs, review applicants and view aggregates
  admin/          User management, platform statistics and audit history
  document/       Income, marksheet and identity verification
  resume/         Resume skills, scores and suggestions
  chat/           Scholarship copilot context and deterministic answers
  storage/        Repository interface, JDBC implementation and demo seed
  http/           Routes to the modules
src/
  app/            Existing pages, forms and animations
  components/     Existing shared UI
  lib/java/       Thin server-side bridge to Java
  lib/datasource/ Frontend data contracts and Java adapter
```

Start reading at `backend/src/main/java/com/scholarai/Main.java`, then `http/PlatformApi.java`, and then the module you want to understand. See [OOP_GUIDE.md](OOP_GUIDE.md) for the actual inheritance, encapsulation, abstraction and polymorphism examples.

## Data and integrations

- **H2 through JDBC** stores data locally under `backend/data/demo/` or `backend/data/live/`. A restart preserves local data. The platform repository stores a small classroom dataset transactionally; financial aid retains its existing dedicated JDBC repository.
- `backend/src/main/resources/demo-data.json` contains the original twelve scholarships, student and applicant queue. Java seeds a new database once; subsequent starts retain edits. Dates remain the original dates, including expired scholarships.
- **Supabase Auth** remains an optional password/session provider. Configure `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_ANON_KEY` for authenticated mode. Registration creates the corresponding account in Java. `SUPABASE_SERVICE_ROLE_KEY` enables the existing private document storage integration.
- **PDF extraction** stays in the frontend server adapter using `unpdf`; Java performs verification and resume scoring. Image-only documents remain pending; there is no OCR service pretending to verify them.
- **Anthropic** remains optional through `ANTHROPIC_API_KEY`. Without it, the Java copilot supplies the original deterministic responses.
- **No Postgres/Drizzle setup is required.** This rebuild replaces that data layer; it does not automatically import records from an older Postgres installation. The existing public demo has no hosted database to migrate.

## Commands

| Command | Purpose |
| --- | --- |
| `npm run dev` | Start Java and the frontend together |
| `npm run build:java` | Test and package Java |
| `npm run build` | Build the frontend; no running Java server needed |
| `npm start` | Start both services with the built frontend |
| `npm run test:java` | Java rules, parity, persistence and workflow tests |
| `npm test` | Frontend transport, rate-limit and aid-action tests |
| `npm run typecheck` | Check frontend types |
| `npm run lint` | Check frontend code |
| `npm run verify:deploy` | Check a running Java service and signed catalogue request |

The Vercel demo continues to use the existing container configuration, which runs both services together. Container data is temporary and can reset when the container is replaced. See [DEPLOYMENT.md](DEPLOYMENT.md).

## Reference

The original interface comes from [ishu7w/ScholarAI](https://github.com/ishu7w/ScholarAI). This project lives in the separate [scholarship-financial-aid-system](https://github.com/ishu7w/scholarship-financial-aid-system) repository.
