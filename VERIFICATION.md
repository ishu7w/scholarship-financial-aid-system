# Java rebuild verification

## Automated checks

- **67 Java tests pass** under JDK 21 with Java 17 compilation compatibility, including the retained financial-aid tests.
- **33 frontend tests pass**, covering signed Java transport, financial-aid server actions and authentication throttles.
- Type checking and ESLint pass without errors or warnings.
- The production Next.js build passes without needing a running Java service during compilation.
- A clean dependency installation targeting Linux x64 passes. The lockfile retains Linux-specific optional dependencies needed by CI and the Vercel image.
- CI builds and tests Java, checks and builds the frontend, starts both services, and exercises the page/API smoke tests.

## Behaviour preservation

`EngineParityTest` compares the Java engine against reference outputs captured from the original TypeScript engine for **61 profiles and all 12 scholarships**. It checks scores, detailed components, ranked matches, eligibility explanations, fraud results and roadmaps. Decimal rounding preserves the original JavaScript results.

Java workflow tests cover profile validation, ownership, file-backed H2 persistence after reopening, concurrent duplicate submissions, frozen scoring snapshots, withdrawal, institution decisions, notifications, program creation/editing, account roles, document ownership and copilot grounding. HTTP tests cover signed requests, replay rejection, invalid bodies and long resume inputs.

## Browser checks

Using Chromium at a 1440-pixel desktop width:

- Home, catalogue, student dashboard, explorer, scholarship detail, profile, documents, resume analyzer, financial aid, login and registration load successfully with no browser runtime errors.
- Profile changes save and survive a reload; checkbox values reach Java correctly.
- Scholarship submission survives a reload and withdrawal removes the application.
- The sample resume is analyzed through Java: ATS score 94, resume score 88.
- The copilot returns a profile-score answer from Java.
- Financial-aid assessment loads the existing eligible programs.

At a 390-pixel mobile width, the explorer has no horizontal overflow.

The home page, explorer and financial-aid page were compared with the existing Vercel deployment. Sampled typography, colours, padding and control dimensions match. Shared components, global styles, assets and the financial-aid view remain unchanged; existing animation declarations are retained. The profile demo notice now describes its persisted shared state instead of saying edits are discarded.

## Scope and limitations

Supabase credential/email flows, actual private-bucket uploads and Anthropic responses were not exercised against external accounts. Their adapters are retained; the Java account, verification and copilot logic is covered locally. Demo account selection, credential validation, sign-out state, authenticated-mode isolation, local file storage, path validation and storage limits have additional coverage.

The H2 classroom data layer replaces Postgres; it does not import an existing hosted database. Vercel container data remains ephemeral. The PR does not merge itself or replace the production deployment.

## Demonstration readiness update

Browser checks now also cover switching between the three demo roles, uploading a PDF and displaying Java verification results, submitting a scholarship and approving it from the institution account, and seeing the approved status back on the student account. Financial aid was exercised through submission, review, approval and simulated disbursement, including reload persistence. Sign-out redirects protected student pages to login. Expired scholarships and decided applications no longer offer invalid application/withdrawal actions.

See `DEMO_WALKTHROUGH.md` for the presentation sequence and sample-account limitations.

The production-mode browser run also confirmed profile save/reload, institution program creation/editing/closure, and a 390-pixel login layout without horizontal overflow.

Shared storage is tested with two repository instances, concurrent-write retries, credential rejection, duplicate aid submissions and stale-version rejection. The resumed free database has an isolated demo state/configuration table; existing project tables are preserved.
