# AMRIT - Consent-API

Consent-API is the AMRIT consent manager for the Digital Personal Data Protection Act, 2023
(DPDPA). It is the only AMRIT service that stores or decides consent. See the approved
proposal: [docs/proposal/consent-manager-proposal.md](docs/proposal/consent-manager-proposal.md).

Delivered so far:

- `001-service-skeleton`: login, the response standard, health/version checks, configuration
  and API documentation.
- `002-mmu-consent-capture`: the MMU consent dialog's endpoints (front-end APIs 2–7).
- `003-patient-consent-management`: the preference centre (front-end APIs 8–14) and the
  doctor's access request.

| Method | Path | Purpose |
|---|---|---|
| GET | `/consent/v1/notices/active/languages?context=` | Languages the active notice is published in |
| GET | `/consent/v1/notices/active?context=&lang=` | Active notice, sections, fingerprint and purposes |
| POST | `/consent/v1/otp/challenges` | Send an OTP (through Common-API's consent OTP) |
| POST | `/consent/v1/otp/challenges/{id}/verify` | Verify an OTP |
| POST | `/consent/v1/evidence` | Upload a voice clip or photo (multipart; encrypted at rest) |
| POST | `/consent/v1/consents` | Save Accept or Decline per purpose |
| GET | `/consent/v1/principals/{benRegId}/consents` | A patient's current consents and history |
| GET | `/consent/v1/principals/{benRegId}/pending-actions?context=` | Consent still needed (missing, legacy, re-consent, guardian-to-self) |
| POST | `/consent/v1/principals/{benRegId}/preferences` | Turn purposes on (with OTP) or off |
| POST | `/consent/v1/principals/{benRegId}/withdrawals` | Withdraw purposes or `ALL`; ends doctor access for them |
| GET | `/consent/v1/principals/{benRegId}/access-grants?status=` | A patient's doctor access grants |
| POST | `/consent/v1/access-grants` | A doctor requests time-limited access |
| POST | `/consent/v1/access-grants/{grantId}/approve` | Patient or guardian approves by OTP |
| POST | `/consent/v1/access-grants/{grantId}/revoke` | Cancel a pending or active grant |

Every write (`POST` above, except verify) needs an **`Idempotency-Key`** header (a UUID): a
retry with the same key returns the original answer and never saves, sends or stores twice.

## Tech stack

Java 17 · Spring Boot 3.2.2 · Spring Data JPA (MySQL 8, `db_identity`) · Redis (shared with
Common-API) · jjwt 0.12.6 · springdoc-openapi 2.3.0 · Lombok · WAR (Wildfly) or `java -jar`.

## Build and run

```bash
# 1. Local configuration (git-ignored; never commit real values)
cp src/main/environment/common_example.properties src/main/environment/common_local.properties
#    edit spring.datasource.*, spring.redis.*, jwt.secret (same as Common-API), cors.allowed-origins

# 2. Build: checkstyle, tests, JaCoCo >= 99 % line and branch coverage
mvn clean install -DENV_VAR=local

# 3. Run on port 8095
mvn spring-boot:run -DENV_VAR=local
```

The environment is chosen with `-DENV_VAR=<env>`, as in every AMRIT service:
`src/main/environment/common_<env>.properties` is merged into `application.properties` at
build time. Files: `common_example` (template), `common_local` (git-ignored), `common_ci`
(dummy values for CI) and `common_docker` (runtime environment variables).

Swagger UI (where enabled): `http://localhost:8095/swagger-ui.html`.

### Configuration keys

| Key | Required | Notes |
|---|---|---|
| `spring.datasource.url` / `username` / `password` | yes | `db_identity` only; no schema changes (`ddl-auto=none`) |
| `spring.redis.host` | yes | the Redis instance Common-API uses |
| `spring.redis.port`, `spring.redis.password` | default `6379` / empty | same keys as Common-API |
| `jwt.secret` | yes | same signing secret as Common-API (at least 32 characters) |
| `cors.allowed-origins` | no | comma-separated, `*` wildcard; empty or missing refuses every browser origin |
| `iemr.session.expiry.time`, `iemr.extend.expiry.time`, `enableIPValidation` | defaults `7200` / `true` / `false` | same as Common-API |
| `springdoc.api-docs.enabled`, `springdoc.swagger-ui.enabled` | default `false` | enable in non-production environments |
| `consent.request.max-body-bytes` | default `1048576` | larger bodies get 413 |
| `consent.health.check-timeout-ms` | default `1500` (100–1900) | keeps `/health` under 2 s |
| `consent.redis.timeout-ms` | default `1500` | Redis command timeout |
| `consent.rate-limit.retry-after-seconds` | default `60` | `Retry-After` on 429 |
| `consent.encryption.key` | yes (secret) | Base64 32-byte AES key for personal fields and evidence files |
| `consent.evidence.storage-dir` | yes | writable directory (a mounted volume) for encrypted evidence |
| `consent.common-api.base-url`, `consent.common-api.timeout-ms` | URL yes / `3000` | Common-API, used for OTP |
| `consent.evidence.max-bytes`, `voice-min-seconds`, `voice-max-seconds` | `10485760` / `30` / `60` | TODO-DPO |
| `consent.capture.max-skew-minutes` | `1440` | device time further off is flagged (TODO-DPO) |
| `consent.otp.ttl-seconds`, `retention-minutes`, `max-attempts` | `300` / `30` / `5` | TODO-DPO |
| `consent.notice.audio-base-url` | empty | prefix for notice audio links |
| `consent.access-grant.max-days` | `30` | longest doctor access a patient can approve (TODO-LEGAL) |
| `consent.access-grant.replay-hours` | `24` | how long an approve or revoke request key is remembered |

A missing required setting stops start-up with `Missing required setting: <name>` (the
value is never printed).

### Docker

```bash
mvn clean package -DENV_VAR=docker -DskipTests
docker build -t consent-api:local .
docker run --rm -p 8095:8080 -e DATABASE_URL=... -e DATABASE_USERNAME=... -e DATABASE_PASSWORD=... \
  -e REDIS_HOST=... -e JWT_SECRET_KEY=... -e CORS_ALLOWED_ORIGINS=... -e SWAGGER_DOC_ENABLED=false \
  -e CONSENT_ENCRYPTION_KEY=... -e EVIDENCE_STORAGE_DIR=/data/evidence -e COMMON_API_BASE_URL=... \
  -v consent-evidence:/data/evidence \
  consent-api:local
```

The variable names are the same as Common-API's.

## Login

Consent-API accepts the existing AMRIT login; there is no separate login. The first
credential found is used, in Common-API's order:

1. `Jwttoken` cookie, then 2. `JwtToken` header (web UIs). An `Authorization` header sent
   with them must also be a live Redis session.
3. Mobile apps: `User-Agent` containing `okhttp` or `java/` plus the `Authorization` session key.

Public paths (no login): `/health`, `/version`, and the API docs where enabled. Everything
else, including unknown paths, returns 401 without a valid login.

## Response standard

Every response, including refusals by the login and origin checks, uses one body:

```json
{
  "success": false,
  "statusCode": 401,
  "message": "Session expired. Please login again",
  "data": null,
  "error": { "code": "SESSION_EXPIRED", "purposeCode": null, "details": null },
  "timestamp": "2026-10-29T10:00:00Z",
  "traceId": "4f1c2a9b7d3e8f60"
}
```

- `statusCode` always equals the HTTP status; the legacy 5000–5010 codes are never used.
- Clients act on `error.code`, never on the message text.
- `traceId` equals the `X-Trace-Id` response header and appears in the server logs.

Error codes (additive only; never renamed or reused):

| HTTP | Codes |
|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST` |
| 401 | `UNAUTHENTICATED`, `SESSION_EXPIRED` |
| 403 | `ACCESS_DENIED`, `ORIGIN_NOT_ALLOWED`, `CONSENT_NOT_FOUND`, `CONSENT_DENIED`, `CONSENT_WITHDRAWN`, `CONSENT_EXPIRED`, `RECONSENT_REQUIRED`, `GUARDIAN_REQUIRED` |
| 404 | `NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `STALE_VERSION`, `STATE_CONFLICT` |
| 413 | `PAYLOAD_TOO_LARGE` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 422 | `BUSINESS_RULE_VIOLATION` |
| 429 | `RATE_LIMITED` |
| 500 | `INTERNAL_ERROR` |
| 422 | `OTP_EXPIRED`, `OTP_INVALID`, `EVIDENCE_HASH_MISMATCH` (also `BUSINESS_RULE_VIOLATION`) |
| 503 | `DEPENDENCY_UNAVAILABLE` |

The full API description is served by Swagger UI at `/swagger-ui.html` (and `/v3/api-docs`)
when `springdoc.swagger-ui.enabled=true`.

## Database

`cm_` tables in `db_identity` come from AMRIT-DB migration
`dbidentity/V33__Consent_Manager_Capture.sql` and (doctor access grants)
`dbidentity/V34__Consent_Access_Grant.sql`. Demo notice and purpose text (placeholders,
`TODO-LEGAL`) is a manual script for dev/demo databases only:
`AMRIT-DB/src/main/resources/db/consent_demo/01_consent_demo_seed.sql`.

## Contributing

- Commit messages follow Conventional Commits (`commitlint.config.js`). Enable the hook once
  with `git config core.hooksPath .git-hooks`; never use `--no-verify`.
- Code must pass `checkstyle.xml`, and new code needs at least 99 % line and branch coverage
  with mock-based tests (no real database or Redis).

## License

GNU General Public License v3.0. See [https://www.gnu.org/licenses/](https://www.gnu.org/licenses/).
