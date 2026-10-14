<!--
Sync Impact Report
- Version change: 1.0.0 → 1.1.0 (MINOR: new principle X, Consent-API and consent-client
  added to scope, guidance materially expanded for the DPDPA Consent Manager programme)
- Modified principles:
  - II. Follow Existing Structure & Coding Standards — added "New services" rule
    (Consent-API mirrors Common-API)
  - III. No Regressions — added consent-enforcement opt-in rule and the consent-denial
    response convention (statusCode 5403 + reasonCode)
  - VII. API Contract & Cross-Service Safety — added consent-client shared library rules
  - VIII. Database Changes Through AMRIT-DB Only — added cm_ table rules, append-only
    exception, DB-grant rule for audit tables
  - IX. Security & Personal Data Protection — expanded with consent-first, purpose-code,
    field-level encryption, evidence storage, data-access logging, sensitive-programme rules
- Added principles: X. Traceability to the Approved Consent Proposal (NON-NEGOTIABLE)
- Added sections: Consent-API Constraints (inside Technology & Repository Constraints)
- Removed sections: none
- Deferred TODOs:
  - Legal decisions D-01..D-11 and retention periods (§13) — kept as configuration with
    placeholder values until Legal/DPO sign-off (see Principle X)
  - Field-level encryption of existing beneficiary tables — separate programme, out of
    Consent-API scope
-->

# AMRIT Back-End Constitution

Applies to every AMRIT back-end repository (Common-API, Inventory-API, MMU-API, AMRIT-DB,
Consent-API and every other AMRIT `*-API` service such as Identity-API, Admin-API, TM-API,
HWC-API, ECD-API, FLW-API, FHIR-API, Helpline104-API, Helpline1097-API, Scheduler-API),
and to the shared `consent-client` library consumed by them.

- `Common-API` is the gateway consumed by every AMRIT front-end.
- `AMRIT-DB` owns the schema of the shared MySQL databases (`db_iemr`, `db_identity`,
  `db_reporting`, `db_1097_identity`) used by all services.
- `Consent-API` is the single Consent Manager for HWC, MMU, TM, ECD, 104 and 1097. It is
  the only service that stores or decides consent, built to the approved proposal
  "AMRIT Consent Manager — DPDP Act, 2023" (kept in `Consent-API/docs/proposal/`).
- `consent-client` is the shared library (consent check + `@RequiresConsent`) that every
  service uses to enforce consent.

## Core Principles

### I. Reuse First

- Before writing anything new, search the current repo **and** the other AMRIT back-end
  repos for an existing controller, service, repository, entity, DTO, mapper, util,
  constant, filter or config that already does the job. If one exists, it MUST be reused.
- Use the established shared building blocks instead of re-implementing them:
  `OutputResponse` / existing response wrappers, JWT/session utilities
  (`JwtUtil`, `JwtAuthenticationUtil`, `JwtUserIdValidationFilter`, `CookieUtil`),
  `RestTemplateUtil` / existing HTTP clients, `InputSanitizer` and validators, Redis
  session utilities, existing exception classes (`IEMRException` family) and handlers.
- Consent-API MUST reuse existing platform capabilities through their APIs rather than
  rebuilding them: SMS and OTP via Common-API (`SMSService`, `OTPHandler`), beneficiary
  identity and date of birth via Identity-API, users/roles/facilities via existing masters,
  scheduling via Quartz as already used in Common-API.
- An existing entity or repository for a table MUST be reused; a second entity mapped to
  the same table is forbidden unless justified in the PR.
- No copy-paste duplication. Logic needed in two or more places MUST be extracted once
  (service or util) and called. Consent logic needed by other services lives in
  `consent-client`, never copied into each service.
- URLs, timeouts, feature flags, external credentials and other tunables MUST come from
  `src/main/environment/common_<env>.properties` / `application.properties`, never be
  hard-coded.

**Rationale**: AMRIT services share one data model and near-identical patterns;
duplication multiplies every bug and every fix.

### II. Follow Existing Structure & Coding Standards

- New code MUST live in the package where equivalent features already live, following
  the repo's layering: `controller.*` → `service.*` (interface + `*Impl`) →
  `repository.*`/`repo.*`, with entities in `data.*` and DTOs in `model.*`/`dto.*`
  under the repo's base package (e.g. `com.iemr.common`).
- **New services**: a new AMRIT service is created only from an approved proposal and
  MUST mirror Common-API's skeleton — same layering, `utils` (JWT filter, `OutputResponse`,
  exceptions, Redis), `config` (Swagger, Redis, Quartz, datasource), env-properties merge
  via `-DENV_VAR`, `checkstyle.xml`, commitlint, Dockerfile and GitHub Actions. Consent-API
  uses base package `com.iemr.consent`, one sub-package per proposal module (`purpose`,
  `notice`, `capture`, `check`, `withdrawal`, `grant`, `evidence`, `rights`, `audit`,
  `sync`, `jobs`) under each layer.
- Controllers stay thin: request parsing, validation, auth-header handling and response
  wrapping only. Business logic belongs in services; persistence in repositories.
- Match the surrounding code: same response envelope (`OutputResponse` with
  `statusCode`/`status`/`errorMessage`/`data`), same `@RequestMapping` style and
  `consumes`/`produces` settings, same Swagger/springdoc annotations (`@Operation`),
  same Lombok usage, same Gson/Jackson usage, same SLF4J logging pattern.
- Consent-API endpoints are versioned under `/consent/v1/...` exactly as listed in the
  proposal; a breaking change requires a new version path, never an in-place change.
- Spring Boot 3 / Jakarta EE: use `jakarta.*` APIs, never `javax.*` replacements.
- Every new `.java` file MUST carry the GPLv3 header copied from an existing file; new
  classes carry a Javadoc class comment.
- Code MUST pass the repo's `checkstyle.xml`; commit hooks (Husky / `.git-hooks`,
  commitlint) MUST NOT be bypassed (`--no-verify` is forbidden).
- Do not introduce a new framework, ORM, mapper or HTTP client alongside the one the
  repo already uses.

**Rationale**: Consistency keeps many services maintainable by the same team.

### III. No Regressions (NON-NEGOTIABLE)

- Nothing may break an existing endpoint, request/response shape, status code, session
  or JWT behaviour, role/permission check, scheduled job, integration, or stored data.
- Existing public signatures MUST NOT be renamed, removed or changed in meaning: URL
  paths, HTTP methods, request/response field names and types, `statusCode` values
  (including `5002` for session expiry), service interface methods, entity fields and
  column mappings, property keys.
- New behaviour MUST be opt-in (new endpoint, new optional field with a safe default,
  new method, feature flag/property defaulting to current behaviour) so existing
  callers behave exactly as before.
- **Consent enforcement in existing services** MUST be switched on per service by a
  property (e.g. `consent.enforcement.enabled`, default `false`) and rolled out service by
  service (HWC pilot first), so turning it off restores today's behaviour exactly.
- **Consent-denial response**: a blocked request returns the standard `OutputResponse`
  envelope with `statusCode = 5403` and `data.reasonCode` set to one of
  `CONSENT_NOT_FOUND`, `CONSENT_DENIED`, `CONSENT_WITHDRAWN`, `CONSENT_EXPIRED`,
  `RECONSENT_REQUIRED`, `GUARDIAN_REQUIRED`, plus the purpose code. The HTTP status
  follows the repo's existing convention. Reason codes are additive only; existing codes
  are never renamed or reused.
- The full existing test suite MUST pass and `mvn clean install -DENV_VAR=<env>` MUST
  succeed before a change is considered done.

**Rationale**: AMRIT is live in health facilities and call centres; a broken API stops
patient care across every UI that calls it.

### IV. Additive Changes Only

- Existing code MUST NOT be modified, refactored, reformatted or "cleaned up" as a side
  effect of a task.
- An edit to existing code is allowed only when the task explicitly requires it (a bug
  fix in that code, or a hook point needed to plug in new code, such as adding
  `@RequiresConsent` to an existing endpoint or replacing today's always-"Yes" consent
  value). Such an edit MUST be the smallest possible change and MUST be called out in the
  PR description with the reason.
- No unrelated file, import order, whitespace, `pom.xml` dependency-version or
  properties changes in the diff.

**Rationale**: Small, focused diffs are reviewable and safe to revert.

### V. Minimal Code

- Write only what the requirement needs: no speculative endpoints, unused fields or
  parameters, dead code, commented-out code, `System.out.println`/`printStackTrace`, or
  "might need later" abstractions.
- Each Spec Kit feature implements only its own slice of the proposal; later modules are
  not stubbed in advance.
- No new Maven dependency unless no existing dependency or Spring built-in can do the
  job; a new dependency MUST be justified in the PR and pass the SAST/CodeQL checks.
- Prefer the simplest readable solution; abstraction is added only when Principle I
  demands reuse.

**Rationale**: Every unnecessary line is maintenance and attack surface.

### VI. Tests for Every Implementation (NON-NEGOTIABLE)

- Every new or modified controller, service, repository query, mapper, filter,
  interceptor, aspect, scheduler job and util MUST ship with tests under
  `src/test/java`, mirroring the main package, using the repo's existing stack
  (JUnit 5 + Mockito, Spring Boot Test).
- Tests MUST use mocks and test doubles only: `@ExtendWith(MockitoExtension.class)` with
  `@Mock`/`@InjectMocks` for services, `MockMvc` (standalone or `@WebMvcTest`) for
  controllers, mocked repositories, `RestTemplate`, Redis, JWT utilities and external
  integrations (Common-API SMS/OTP, Identity-API, file storage). No real database, Redis,
  SMS/OTP gateway or third-party call.
- Mock data MUST live in the test class or a co-located fixture/builder, reflect the
  real request/response and entity shapes, and be synthetic.
- Tests MUST cover: happy path, validation/invalid input, missing/invalid auth,
  not-found and empty/null data, repository/integration exceptions, every branch of
  `if`/`switch`/ternary, and the exact `OutputResponse` status codes, reason codes and
  messages returned.
- Every proposal scenario a feature claims (e.g. C1, C18, H5, M8) MUST have at least
  one test whose name or `@DisplayName` contains the scenario ID.
- Consent-specific invariants MUST be tested explicitly: append-only versioning (no
  update/delete of consent rows), single current decision per beneficiary per purpose,
  idempotent capture (same request key → one record), stale-update rejection,
  withdrawal-wins conflict resolution, hash-chain integrity and tamper detection, cache
  invalidation on every consent change, expiry by time (fixed `Clock`).
- Coverage for new/changed code MUST be **≥ 99 %** for lines and branches, verified with
  the JaCoCo report from `mvn test` (and SonarQube where configured). Any line that
  genuinely cannot be covered MUST be justified in the PR.
- Tests MUST be deterministic: an injected `Clock` for all time logic, no sleeps, no
  order dependence, no shared mutable static state.

**Rationale**: High coverage with realistic mocks is the only proof Principle III holds.

### VII. API Contract & Cross-Service Safety (NON-NEGOTIABLE)

`Common-API` is called by every AMRIT front-end, AMRIT services call each other, and
every service will call Consent-API. A change to a shared endpoint, DTO, entity, table or
library ships to all of them.

- Before changing an existing endpoint, DTO, entity or shared table, every consumer
  MUST be searched — the front-end repos (including `Common-UI`) and the other back-end
  repos — and the impact listed in the PR.
- Every new or changed endpoint MUST have its contract written down before
  implementation (path, method, headers, request body, response body, status and reason
  codes, error cases) in the Spec Kit `contracts/` folder as OpenAPI, and documented with
  springdoc annotations. Front-end teams build against this contract with mocks.
- Changes MUST be backward compatible for every consumer: additive fields only, no
  renamed/removed fields, no changed types, defaults or error semantics.
- **consent-client library**:
  - Lives in its own Maven module/artifact, semantically versioned; services pin an
    exact version.
  - Provides the `@RequiresConsent(purpose = "...")` annotation, the check client
    (Redis first, then `POST /consent/v1/check`), data-access logging and the
    consent-denial response of Principle III — nothing else.
  - MUST compile and run on Java 17 / Spring Boot 3.2+ and add no transitive dependency
    a consumer does not already have.
  - Changes MUST be backward compatible; a new release is verified by building and
    testing every consuming service before they upgrade.
- Service-specific logic MUST NOT be placed in Common-API, Consent-API or consent-client
  unless more than one service line needs it; otherwise keep it in the owning service.
  Purpose codes and service-line rules are data (configured in `cm_purpose` / notices),
  not code branches per service.

**Rationale**: One careless shared change can break every AMRIT service line at once.

### VIII. Database Changes Through AMRIT-DB Only (NON-NEGOTIABLE)

- All schema and seed-data changes (tables, columns, indexes, views, procedures, master
  data) MUST be delivered as Flyway migrations in `AMRIT-DB`, in the folder of the target
  schema (`db/migration/dbiemr`, `dbidentity`, `dbreporting`, `db1097identity`).
- Services MUST NOT create or alter the real schemas at runtime: the service's main
  `application.properties` keeps `spring.jpa.hibernate.ddl-auto=none`, and there is no
  DDL in application code. (Existing in-memory profiles such as `application-swagger`
  are left as they are.)
- Migrations MUST be named `V<next-highest>__<Description>.sql`, MUST be idempotent
  (`CREATE TABLE IF NOT EXISTS`, `information_schema` + `PREPARE/EXECUTE` guards for
  columns/indexes) and MUST be additive (no dropping/renaming columns or tables in use).
- A migration that has been applied in any environment MUST NOT be edited or deleted;
  fix forward with a new version.
- If a migration adds a column or table holding personal data, the anonymizer registry
  (`anonymization-registry.yml`) MUST be updated in the same change.
- Entity mappings in the service MUST match the migration exactly; the service PR
  references the AMRIT-DB PR.
- **Consent Manager tables**:
  - All Consent-API tables use the `cm_` prefix and live in `db_identity`
    (`db/migration/dbidentity`).
  - `cm_consent`, `cm_consent_purpose`, `cm_audit_log` and `cm_data_access_log` are
    **append-only**: the application never issues UPDATE or DELETE on them, except
    flipping `is_current` / status to `SUPERSEDED` on `cm_consent_purpose` inside the
    same transaction that inserts the new version. They are exempt from the soft-delete
    `Deleted` / `ModifiedBy` pattern of Principle IX.
  - "Only one current decision per beneficiary per purpose" MUST be enforced by a
    database unique constraint, not only by code.
  - Revoking UPDATE/DELETE on `cm_audit_log` (and `cm_data_access_log`) for the
    application DB user MUST be delivered as a reviewed grant script handed to DevOps,
    and referenced in the PR.
  - Existing patients' consent state (LEGACY marking, replacing today's always-"Yes")
    is changed only by AMRIT-DB data migrations, never by ad-hoc scripts.

**Rationale**: Multiple services share the same production schemas; uncontrolled schema
changes corrupt data or break other services, and consent history is legal evidence.

### IX. Security & Personal Data Protection (NON-NEGOTIABLE)

AMRIT processes beneficiary health and personal data and MUST comply with the Digital
Personal Data Protection Act, 2023 (DPDPA), the DPDP Rules and applicable health-data
guidelines.

- **Authentication**: every new endpoint MUST be protected by the existing authentication
  chain (JWT / session filter, `Authorization` header) and existing role checks; any
  public/unauthed endpoint MUST be explicitly justified in the PR. Break-glass, DPO
  export, rights-request handling and 1097 records require their dedicated roles.
- **Input**: all input MUST be validated and sanitised (`InputSanitizer`, Bean
  Validation, existing validators). Queries MUST use JPA/parameterised queries; no
  string-concatenated SQL.
- **No personal data in logs**: personal data MUST NOT be written to logs, exception
  messages or API error responses (names, phone numbers, Aadhaar/ABHA, addresses, health
  details, OTPs, tokens). Log IDs, not values.
- **Data minimisation and purpose limitation**: endpoints return and store only the fields
  the stated purpose requires.
- **Consent first**:
  - No patient data is collected, read or shared for a purpose until Consent-API
    confirms it is allowed (directly or via `consent-client`).
  - A missing decision, a closed screen or a dropped call is **never** stored as consent.
  - Every purpose is a separate decision; nothing is granted by default.
- **Consent records**:
  - Consent grant, denial, withdrawal, renewal and access decisions are recorded in an
    append-only form (who, what, which purpose, notice version, language, mode, channel,
    proof, when).
  - They are never silently overwritten or hard-deleted.
  - Withdrawal takes effect immediately: the cache is invalidated and dependent jobs
    (SMS, calls, sharing, access grants) stop for that purpose.
- **Audit**:
  - Every consent action is written to the hash-chained `cm_audit_log`.
  - Every allowed or denied access to patient data is written to `cm_data_access_log`.
  - Audit writes MUST be in the same transaction as the action they record.
- **Encryption and masking**:
  - New personal fields in `cm_` tables (guardian/witness names and phones, masked
    numbers, etc.) MUST be encrypted at field level using one shared, configurable
    encryption component.
  - Phone and ID numbers are masked in every API response except where the purpose
    requires the full value.
- **Evidence**:
  - Voice clips, signatures and photos are stored in encrypted file storage, never in
    the database.
  - The row stores only the reference and SHA-256 hash.
  - Hashes are verified on read.
- **OTP**: OTPs are never stored or logged in clear text; OTP send and verify use
  Common-API's existing OTP handling and rate limiting.
- **Sensitive programmes**:
  - 1097 data is restricted-role and masked by default.
  - No SMS or notification text may reveal the programme or a health condition.
  - Anonymous use MUST remain possible where the proposal allows it.
- **Audit columns**: records other than the append-only consent/audit tables MUST carry
  the standard audit columns (`CreatedBy`, `CreatedDate`, `ModifiedBy`, `LastModDate`,
  `Deleted`) and use soft delete, following existing entities.
- **Secrets**: no secrets, keys (including van signing keys and encryption keys), tokens,
  credentials or real patient data in code, tests, fixtures or committed properties;
  `common_example.properties` keeps placeholders only.

**Rationale**: A data leak or unlawful processing harms beneficiaries and exposes the
programme to legal penalties; consent records are the evidence the Act requires.

### X. Traceability to the Approved Consent Proposal (NON-NEGOTIABLE)

- The approved proposal in `Consent-API/docs/proposal/` is the source of requirements.
  Every Spec Kit spec for consent work MUST list the scenario IDs it covers (C1–C24,
  H1–H12, M1–M14, T1–T10, E1–E11, A1–A12, N1–N10), the API endpoints, and the `cm_`
  tables it touches.
- Behaviour not in the proposal MUST NOT be added without an approved proposal change;
  deviations MUST be written in the spec and approved by the tech lead.
- Open decisions (D-01 to D-11), retention periods (§13), grace periods, validity periods
  and offline-snapshot age MUST NOT be invented in code. They are configuration
  (properties or `cm_purpose`/notice data) with placeholder values marked
  `TODO-LEGAL`/`TODO-DPO`, recorded in the spec's assumptions, and listed in the PR
  until signed off.
- Each PR states which scenario IDs it delivers and which decisions it depends on.

**Rationale**: Consent is a legal obligation; every behaviour must trace back to an
approved requirement and every legal value must have an owner.

## Technology & Repository Constraints

- Language/runtime: Java 17. Framework: Spring Boot 3.2.x (AMRIT-DB 3.3.x), Spring Web,
  Spring Data JPA / Hibernate. Build: Maven (`-DENV_VAR=<env>` required), WAR packaging
  for Wildfly, Dockerfile per repo.
- Data: MySQL 8 (schemas owned by AMRIT-DB / Flyway), Redis for session and cache;
  MongoDB only where a repo already uses it.
- Libraries: Lombok, MapStruct (where already used), springdoc-openapi, Gson/Jackson as
  already used by the repo; Quartz for scheduled jobs.
- Testing: JUnit 5, Mockito, Spring Boot Test / MockMvc, JaCoCo; no new test framework
  per repo.
- Quality: Checkstyle (`checkstyle.xml`), SonarQube, CodeQL/SAST GitHub Actions;
  commit messages follow commitlint conventional commits
  (`feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `chore:` …, header ≤ 100 chars).
- Per-environment configuration lives in `src/main/environment/common_<env>.properties`;
  `common_local.properties` stays gitignored. New property keys MUST be added to
  `common_example.properties` (and other committed env files) with placeholder values.

### Consent-API Constraints

- Datasource: `db_identity` (`cm_` tables only). Beneficiary data, users and facilities
  are read through the owning service's API, not by mapping their tables.
- Consent check: answers from the Redis cache when present; every consent change
  invalidates the cached answer on all servers before the API call returns success.
  Latency and availability targets, and the behaviour when Consent-API is unreachable
  (D-04), are defined in the consent-check spec.
- Idempotency: every state-changing endpoint accepts a client request key; a repeat
  with the same key returns the original result and creates no new record or SMS.
- Concurrency: updates to a beneficiary's consent carry the version they were based
  on; a stale version is rejected.
- Time: all timestamps stored in UTC; server time orders conflicts; device time is kept
  as captured and flagged when outside the allowed range.
- Offline sync (MMU): uploads are signed per van, deduplicated by device-generated ID,
  and resolved by the proposal's rules (withdrawal wins over an older or equal consent).
  Local copies are deleted only after central confirmation. The van side lives in
  MMU-API under the same rules.
- Scheduled jobs (expiry, renewal reminders, guardian-to-adult, retention, audit-chain
  check, access-grant expiry) MUST be idempotent and safe to run on more than one node.

## Development Workflow & Quality Gates

A change is done only when all gates pass:

1. **Reuse check** — current repo and other AMRIT back-end repos searched; reuse decision
   noted.
2. **Placement check** — files sit in the established package/layer structure and naming.
3. **Traceability check** — spec lists scenario IDs, endpoints, `cm_` tables and
   dependent decisions; legal values are placeholders with owners.
4. **Contract check** — API contract written in `contracts/` and springdoc; consumers
   searched for any change to an existing contract; impact listed.
5. **Diff check** — diff contains only additive changes, plus explicitly justified
   minimal edits; no unrelated changes.
6. **Database check** — schema changes delivered as idempotent AMRIT-DB Flyway
   migrations; entities match; append-only and unique-current rules enforced in the
   database; anonymizer registry updated for new personal data.
7. **Security & privacy check** — auth on every endpoint, input validated, no personal
   data in logs/responses, field encryption and masking applied, consent and audit
   requirements met, enforcement behind an off-by-default flag in existing services.
8. **Lint** — Checkstyle passes with no new violations.
9. **Tests** — `mvn test` passes; every claimed scenario ID has a test; JaCoCo shows
   new/changed code ≥ 99 % line and branch coverage; existing tests still green.
10. **Build** — `mvn clean install -DENV_VAR=<env>` succeeds; CI (build, CodeQL, SAST,
    commitlint) green.
11. **Review** — PR description states what was added, scenario IDs delivered, any
    existing code touched and why, the API contract, linked AMRIT-DB PR (if any),
    coverage numbers, open legal decisions relied on, and cross-service impact
    (or "none").

## Governance

- This constitution overrides informal practice for all AMRIT back-end work, including
  work done by AI assistants (Spec Kit specs, plans, tasks and implementations MUST check
  against it; `/speckit.analyze` findings against it MUST be resolved before
  `/speckit.implement`).
- Every PR review MUST verify compliance with Principles I–X; non-compliance blocks
  merge unless an exception is written in the PR and approved by the tech lead.
  Changes affecting consent, audit or personal data also need DPO review.
- Amendments: proposed via PR to this file with rationale, approved by the BE tech lead,
  and recorded in the Sync Impact Report. The same file is kept in each repo's
  `.specify/memory/constitution.md`; all copies MUST stay identical.
- Versioning: MAJOR for removing/redefining a principle, MINOR for adding a principle or
  materially expanding guidance, PATCH for wording/clarifications.
- Compliance is reviewed whenever a new back-end repo, a new Spring Boot/Java major
  version, a change to the consent proposal, or a change in applicable data-protection
  law is adopted.

**Version**: 1.1.0 | **Ratified**: 2026-10-14 | **Last Amended**: 2026-10-14
