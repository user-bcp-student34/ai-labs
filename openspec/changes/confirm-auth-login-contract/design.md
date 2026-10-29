# Design

## Context

See [proposal.md](proposal.md) for the motivation and [specs/auth-login/spec.md](specs/auth-login/spec.md) for the protected observable requirements.

`AuthResource` exposes `POST /auth/login`, validates the request and maps service outcomes to HTTP 400, 200, 401, or 500. `AuthService` looks up the user, verifies the password, escapes `displayName`, and builds `LoginResponse` using `TokenService`. The token currently expires after 3600 seconds. `docs/ENDPOINTS.md` is the behavioral baseline; confirmation with external consumers is not presumed complete.

`AuthResourceTest` uses `@QuarkusTest` and RestAssured for HTTP-level assertions. Existing tests cover the principal success, validation, invalid-credential, controlled-service-error, and CORS outcomes. `docs/TESTING.md` records 17 focused tests and a 32-test `verify` run with JaCoCo passing at 93% overall and 100% for `AuthResource`. Authentication tests use the isolated test-mode SQLite seed.

## Goals / Non-Goals

**Goals:**

- Preserve the route, status codes, documented messages, response fields, and CORS behavior in the spec.
- Use HTTP-level characterization as the control before any change to critical authentication behavior.
- Keep any future discrepancy resolution within `auth/` and require an explicit contract decision before changing observable behavior.

**Non-Goals:**

- Redesigning authentication, JWT claims/signing, persistence, authorization, CORS policy, or the application architecture.
- Defining response bodies for CORS 403 or parser/media-type failures that are unspecified by the baseline.
- Changing production code, tests, dependencies, or general documentation in this design-only step.

## Protected Contracts

| Surface | Protected behavior |
|---|---|
| `POST /auth/login` request | JSON request; no pre-existing Authorization header; non-blank `username` and `password`; `client` remains optional. |
| Validation | Null request, missing/null/blank required fields return HTTP 400 with the documented `message`. |
| Authentication | Credentials that do not authenticate return HTTP 401 with `Invalid user <username> or password`. |
| Success | HTTP 200 with `token`, `expiresIn` equal to 3600, `user.username`, `user.displayName`, `user.role`, and `debug` equal to `authenticated-with-legacy-flow`. The token is dynamic and opaque. |
| Controlled internal failure | HTTP 500 with generic `message` and `exception: INTERNAL_ERROR`; internal exception details are not part of the response. |
| Disallowed origin | HTTP 403 without `Access-Control-Allow-Origin`; response body remains unspecified. |

## Components Affected

- `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`: HTTP boundary and status/response mapping; change only if characterization proves divergence from the approved contract.
- `auth/src/main/java/com/bcp/workshop/auth/AuthService.java`: authentication outcome and success response construction; no redesign is planned.
- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: HTTP characterization for the contract scenarios.
- `UserRepository`, `TokenService`, and `application.properties` are dependencies of the flow but are not planned change targets. `api/`, `web/`, and general documentation are outside the affected area.

## Decisions

### Use HTTP-level characterization as the primary contract check

Use the existing `@QuarkusTest` and RestAssured pattern to observe status, serialized JSON, and relevant response headers through the real HTTP boundary. Direct resource tests may supplement this coverage, but will not replace it because they bypass transport serialization and framework behavior.

**Alternative considered:** test only `AuthResource` directly. This is narrower, but would not establish the endpoint contract at the HTTP boundary.

### Treat the documented baseline as authoritative until a consumer decision exists

Keep `docs/ENDPOINTS.md` and the approved `auth-login` spec as the expected outcomes. Do not infer a contract change from implementation details or add response guarantees unsupported by the document. If an observed result conflicts with the baseline, record the evidence and resolve the contract before changing production behavior.

**Alternative considered:** redefine statuses or response shapes immediately to match a presumed consumer expectation. Rejected because no such confirmed expectation is available in this change.

### Keep tokens opaque and test data isolated

Assert that a successful response contains a non-empty token, but do not log it or compare an exact token value. Use test-mode fixtures only; do not introduce production credentials or secrets.

**Alternative considered:** assert a literal token or add a real secret to the test configuration. Rejected because token values are dynamic and secrets must remain externalized.

### Do not expand unspecified error contracts

Keep the CORS rejection assertion to HTTP 403 and absence of `Access-Control-Allow-Origin`. Do not assert a 403 body or define parser/media-type error responses unless separately agreed.

**Alternative considered:** freeze framework-generated error bodies as API behavior. Rejected because they are not defined by the current baseline.

## Incremental Strategy and Characterization Tests

1. Before changing production behavior, map each scenario in `specs/auth-login/spec.md` to an HTTP test in `AuthResourceTest` and run the focused baseline suite.
2. Close characterization gaps only within `auth/`: explicitly cover null `username` and `password` values; assert the documented status and message for validation/authentication outcomes; check the successful response fields and JSON media type; check `client` omitted; verify the generic 500 response and the 403 header behavior without asserting an unspecified body.
3. Keep the token assertion value-agnostic and ensure test output does not contain token or secret values. Use the existing test-mode SQLite fixtures.
4. Run `mvn -B -ntp -Dtest=AuthResourceTest test` from `auth/`. If production code is changed after a contract discrepancy is resolved, run `mvn -B -ntp verify` from `auth/` to exercise the configured JaCoCo gate of at least 85% per class.
5. If the characterization tests confirm the baseline, make no production behavior change. Any mismatch blocks implementation until the agreed contract is clarified and the spec updated.

## Risks / Trade-offs

- **Consumer confirmation remains pending** → Preserve the documented baseline and require an explicit decision before any externally visible deviation.
- **Error messages are observable contract data** → Assert the documented values, while avoiding guarantees for unspecified parser and CORS bodies.
- **Tokens and credentials are sensitive or dynamic** → Use isolated test fixtures, avoid logging values, and assert only token presence/non-emptiness.
- **SQLite seed behavior is test-mode-specific** → Keep characterization in the component's Quarkus test environment; do not use production credentials or database state.
- **Current coverage can mask missing assertions** → Use the spec scenario matrix as the coverage checklist rather than relying on coverage percentage alone.

## Rollback

This design introduces no deployment, dependency, or data migration, so no operational rollback is required for the artifact itself. If a later implementation changes production behavior and must be reverted, revert only that `auth/` implementation change and retain characterization tests that match the approved contract. Remove or revise such tests only after an explicit contract change.