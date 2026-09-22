# Java OOP guide

The browser renders the existing frontend. Its server actions call Java; they do not calculate scholarship scores or make application decisions. The Java project uses the JDK HTTP server, Jackson for JSON, H2 and JDBC. There is no Spring configuration, dependency-injection framework or remote database to learn for the demo.

## Four OOP concepts in the running project

| Concept | Actual class or interface | What it does |
| --- | --- | --- |
| Encapsulation | `aid/domain/AidApplication` | Private state changes only through permitted lifecycle methods; an approved amount and status cannot be changed arbitrarily. |
| Abstraction | `storage/PlatformRepository`, `aid/repository/AidRepository` | Services ask repositories to load or update records without knowing the connection or SQL details. |
| Inheritance | `NeedBasedGrant`, `EmergencyAid`, `EducationSupport` extend `AidProgram` | Each aid program inherits common identity and assessment behaviour and supplies its own eligibility rules. |
| Polymorphism | `FinancialAidService` works with `AidProgram` references | The same assessment call runs the concrete program's rules, chosen by the actual object. |

Composition is also used throughout: `ApplicationService` contains a repository, `ScoreService` and `MatchingService`. It coordinates them instead of duplicating their work.

## Reading order

1. **`Main.java`** creates the repositories, services and HTTP server.
2. **`http/PlatformApi.java`** selects a module for a signed request. The existing financial-aid routes stay in `aid/http/AidHttpServer.java`.
3. **`student/StudentProfile.java`** and **`scholarship/Scholarship.java`** define immutable data records. A Java record supplies accessors, equality and a constructor without pages of getter/setter boilerplate.
4. **`scoring/ScoreService.java`** calculates the ten published score components.
5. **`scholarship/MatchingService.java`** applies eligibility gates, computes matches and sorts them.
6. **`application/ApplicationService.java`** submits, withdraws and saves scholarships. Submission freezes the score and reasons so a later profile edit cannot rewrite the decision evidence.
7. **`aid/domain/AidProgram.java`** and its three subclasses demonstrate inheritance and polymorphism.
8. **`storage/JdbcPlatformRepository.java`** shows the JDBC transaction around each platform update.

## Example: applying for a scholarship

```text
Apply button
  → Next.js applyAction (uses the signed-in identity)
  → signed request to PlatformApi
  → ApplicationService.submit
      checks student role, deadline and duplicate application
      loads the student's stored profile
      calls ScoreService and MatchingService
      stores the frozen score, reasons and submitted application
      records an audit entry
  → the existing page refreshes
```

## Example: financial aid

```text
FinancialNeed
  → FinancialAidService
  → AidProgram.assess
      NeedBasedGrant / EmergencyAid / EducationSupport
  → AidApplication
  → AidRepository → JdbcAidRepository
```

The three concrete programs run through the same abstract type. No UI code needs to know which subclass supplies a rule.

## Simple storage design

`PlatformRepository` exposes `read()` and `update(operation)`. The JDBC implementation loads a small JSON dataset from H2, runs a service operation within a transaction, and commits the resulting dataset. A failure rolls back the entire operation. Row locking prevents two simultaneous submissions from passing the duplicate check.

This is intentionally for a small college demo. The flexible JSON records keep institution and admin screens compatible with the existing frontend without introducing many mapper classes. Scoring uses typed immutable Java records. A larger deployment would split the platform dataset into relational tables behind the same repository interface.

The financial-aid repository already uses dedicated SQL records and optimistic version checks; that tested implementation is retained.

## Tests worth showing in a viva

- `EngineParityTest`: compares Java results with snapshots captured from the original frontend for all 61 seeded profiles and 12 scholarships.
- `EngineRulesTest`: checks score bounds, increasing CGPA, increasing income, demographic neutrality, fraud and roadmaps.
- `PlatformWorkflowTest`: tests validation, ownership, persistence after reopening the repository, duplicate submission, frozen scores, decisions and notifications.
- `DocumentTest`: checks Indian income formats, CGPA conversion, identity matching and unreadable documents.
- Existing aid domain/service/HTTP tests cover grant rules, lifecycle transitions, persistence, replay protection and signed requests.

These explanations belong in repository documentation only. The website contains no added project-structure panels.
