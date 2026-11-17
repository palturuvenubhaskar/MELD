# Engineering Rules

These rules apply to all contributors and AI coding agents in this repository.

## General
1. Read relevant documentation and inspect existing code before editing.
2. Keep changes focused; avoid unrelated refactoring.
3. Prefer simple maintainable solutions.
4. Do not add dependencies without explaining why.
5. Do not silently change existing behavior or API contracts.
6. Do not claim success unless relevant commands and tests were actually run.
7. State assumptions or ask when requirements are unclear.

## Architecture
- Begin with a modular monolith; do not introduce microservices prematurely.
- Keep business logic out of controllers.
- Controllers handle HTTP concerns and delegate to application services.
- Keep module boundaries clear.
- Use interfaces where they create real extension points, such as model providers and workflow nodes.
- Keep workflow execution independent of specific AI providers.
- Avoid direct cross-module repository access.

## Java and Spring Boot
- Use the Java version configured by the repository.
- Prefer constructor injection.
- Use descriptive names and small, focused methods.
- Validate incoming request DTOs.
- Use DTOs for API input/output; never expose JPA entities directly.
- Centralize exception handling.
- Use transactions at appropriate service boundaries.
- Avoid field injection, static global state and broad exception swallowing.
- Use `BigDecimal` for money.
- Use timezone-aware timestamps and persist consistently in UTC.
- Do not block HTTP request threads for long-running workflows.

## Database
- Use Flyway for every schema change.
- Never rely on Hibernate auto-DDL in production.
- Use UUID identifiers consistently.
- Add appropriate foreign keys, uniqueness constraints and indexes.
- Keep workspace ownership explicit.
- Enforce matching workspace ownership with composite foreign keys for relationships such as agent-to-credential, agent-to-tool, and execution-to-workflow/version.
- Store versioned workflow nodes and edges in one canonical representation; do not keep a second authoritative workflow definition.
- Published workflow versions and their node/edge rows are immutable. Create a new version for edits, and snapshot non-secret agent settings into the version.
- Do not store important relational data only in JSON.
- Never edit an already-applied migration; add a new migration.
- Review destructive migrations for data-loss risk.
- Paginate potentially large history and log queries.
- PostgreSQL is the source of truth for workflow/execution state.

## API
- Version endpoints under `/api/v1`.
- Use correct HTTP methods and status codes.
- Validate requests and return consistent errors.
- Never return secrets, password hashes or internal stack traces.
- Document non-obvious behavior.
- Authenticate and authorize every protected resource.
- Verify workspace access on every workspace-owned operation.

## Security
- Never commit API keys, passwords, tokens, certificates or production environment files.
- Keep `.env` out of Git; commit `.env.example` with placeholders only.
- Hash passwords with a suitable password-hashing algorithm.
- Encrypt provider credentials at rest; keep encryption keys outside the database and repository.
- Never send provider secrets to the browser.
- Redact secrets from logs and errors.
- Treat prompts, model output, uploaded content and tool output as untrusted.
- Validate tool schemas and restrict permissions.
- Apply request, token, execution-time and tool-call limits.
- In the MVP, limit tools to read-only or low-risk actions. Keep sensitive or irreversible actions disabled until approval nodes are available; then require approval before execution.
- Run network-capable tools with outbound access denied by default. Allow approved destinations only, reject private/local/link-local/multicast/metadata addresses, pin a vetted DNS result for each connection, and validate every redirect.
- Do not give tool workers unrestricted access to internal networks or cloud instance credentials.
- Never execute arbitrary user-supplied Java code inside the backend JVM.
- Never weaken security controls just to make a demo work.

## Workflow engine
- Use explicit execution and node state machines.
- Persist state transitions.
- Pin each execution to an immutable workflow version, including snapshots of non-secret agent settings.
- Use the PostgreSQL execution row as the durable queue item; workers claim it with an expiring lease and heartbeat, and recovery handles expired leases after restart.
- Hash workflow ID, version ID and normalized input for request idempotency. Reuse the existing execution only for the same key and matching hash; return a conflict for a mismatched request.
- Treat external tool calls as at-least-once. Use a stable invocation key across retries when supported; never automatically retry an uncertain non-idempotent call.
- Mark uncertain node results `OUTCOME_UNKNOWN` and require an operator to resolve them before retry.
- Define retry eligibility and limits per node type.
- Persist cancellation requests and cancel at safe node boundaries; do not imply an in-flight side effect was reversed.
- Apply timeouts and resource limits.
- Keep node inputs and outputs structured and serializable.
- Record useful logs without leaking secrets.
- Bound concurrency and resource consumption.
- Treat external tool calls as slow, unreliable and potentially side-effecting.

## Frontend
- Use TypeScript types for API data and component props.
- Keep API access in a service/data-access layer.
- Never put provider credentials in frontend environment variables.
- The backend, not the UI, is the authorization boundary.
- Include loading, empty, success and error states.
- Validate forms and show actionable messages.
- Split large components by feature and responsibility.
- Keep workflow node configuration typed.
- Do not hardcode secrets or production URLs.

## Testing and quality
- Add tests for new business behavior and failure paths.
- Test workspace isolation for new resource endpoints.
- Test database constraints against mismatched workspace relationships and cross-version workflow edges.
- Test restart recovery, duplicate idempotency keys, unknown tool outcomes and safe cancellation boundaries.
- Test that published versions remain unchanged after agent edits.
- Test that tool egress blocks internal/metadata addresses and forbidden redirect targets.
- Use unit tests for domain rules and integration tests for persistence/security boundaries.
- Do not remove or weaken tests just to obtain a passing build.
- Fix flaky tests rather than blindly rerunning them.
- Run formatting, static checks and relevant tests before submitting.
- Remove debug output, dead code and unused imports.

## Git and review
- Use focused commits with descriptive messages.
- Do not commit build output, local databases, logs or secrets.
- Include purpose, changes, tests and known limitations in pull requests.
- Mention migrations and configuration changes explicitly.
- Do not force-push shared branches without agreement.

## AI coding-agent operating rules
Before editing: inspect repository, read relevant docs, identify likely files and state assumptions/plan.

While editing: follow existing conventions, avoid unrelated changes, do not invent APIs, and do not overwrite user work.

After editing: run tests/builds, inspect the diff, list changed files, report exact commands/results, disclose untested behavior and update docs.

If a command cannot be run, say so. Never fabricate test results.
