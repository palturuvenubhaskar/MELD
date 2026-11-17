# Testing Strategy and Test Plan

## 1. Purpose and priorities
Verify the platform from unit behavior through complete user journeys. Prioritize:
1. Security and workspace isolation.
2. Workflow execution correctness.
3. Durable state and recovery.
4. Provider/tool failure handling.
5. Core user experience.

## 2. Test levels

### Unit tests
Use JUnit 5 and Mockito for domain rules, workflow validation, state transitions, node behavior, retry eligibility, permission decisions and input mapping. Keep unit tests fast and independent of external providers/database.

### Integration tests
Use Spring Boot test support and Testcontainers where practical for PostgreSQL persistence, Flyway migrations, repository queries, transactions, authentication, authorization, workspace isolation and execution state.

### API tests
Verify status codes, request validation, response schemas, authentication, authorization, pagination, filtering and consistent errors.

### Frontend tests
Test authentication forms, agent creation/editing, workflow node creation/connection, validation messages, execution status and loading/empty/error states.

### End-to-end tests
Verify registration, workspace creation, provider configuration, agent testing, workflow creation, execution, log inspection and controlled failure/recovery.

## 3. Functional test cases

| ID | Area | Scenario | Expected result | Priority |
|---|---|---|---|---|
| TC-001 | Auth | Register valid details | Account created | P0 |
| TC-002 | Auth | Register existing email | Safe conflict/validation response | P0 |
| TC-003 | Auth | Login valid credentials | Authenticated session/token | P0 |
| TC-004 | Auth | Incorrect password | Rejected without leaking secrets | P0 |
| TC-005 | Workspace | Create workspace | Workspace and owner membership created | P0 |
| TC-006 | Workspace | Access without membership | Access denied | P0 |
| TC-007 | Agent | Create valid agent | Saved in current workspace | P0 |
| TC-008 | Agent | Missing required prompt | Validation error | P0 |
| TC-009 | Provider | Configure valid credentials | Secret stored securely | P0 |
| TC-010 | Provider | Invalid credentials | Safe actionable error | P0 |
| TC-011 | Workflow | Create valid nodes/edges | Workflow saved | P0 |
| TC-012 | Workflow | Edge references missing node | Validation fails | P0 |
| TC-013 | Workflow | Unsupported cycle | Validation rejects workflow | P0 |
| TC-014 | Execution | Run valid sequential workflow | Nodes run in expected order | P0 |
| TC-015 | Execution | Invalid input | Consistent rejection/failure | P0 |
| TC-016 | Execution | Provider timeout | Useful failure is persisted | P0 |
| TC-017 | Execution | Tool returns error | Node and execution failure recorded | P0 |
| TC-018 | Execution | Retrieve execution | Correct status/input/output | P0 |
| TC-019 | Logs | Retrieve logs | Ordered and scoped to execution | P0 |
| TC-020 | Retry | Retry eligible failed node | New attempt recorded | P0 |
| TC-021 | Retry | Retry unsafe side-effecting node | Blocked or requires explicit safe policy | P0 |
| TC-022 | Cancel | Cancel a pending or running execution | Pending run cancels immediately; running run cancels at the next safe node boundary, without claiming an in-flight side effect was reversed | P0 |
| TC-023 | Versioning | Edit workflow or reusable agent after publishing/running | Existing version and its agent settings snapshot remain unchanged; edits create a new version | P0 |
| TC-024 | Approval | Reach approval node | Execution pauses for decision | P1 |
| TC-025 | Approval | Unauthorized approval decision | Rejected | P1 |
| TC-026 | Usage | Provider returns token usage | Usage stored when available | P1 |
| TC-027 | Idempotency | Submit simultaneous requests with the same key, workflow, version and normalized input | Both receive the same execution; only one execution is created | P0 |
| TC-028 | Idempotency | Reuse key with different workflow, version or input | Conflict returned; original execution is unchanged | P0 |
| TC-029 | Recovery | Restart worker with queued or leased execution | Queued work is claimed after restart; expired lease follows recovery policy | P0 |
| TC-030 | Recovery | Worker stops during a non-idempotent tool call | Node becomes `OUTCOME_UNKNOWN`; no automatic replay occurs | P0 |
| TC-031 | Tenant integrity | Link agent/credential/tool or execution/workflow across workspaces | Database constraint rejects the relationship | P0 |
| TC-032 | Version integrity | Edge points to a node in another workflow version | Database constraint rejects the edge | P0 |

## 4. Security test cases

| ID | Scenario | Expected result |
|---|---|---|
| SEC-001 | User A requests User B's workspace | Denied |
| SEC-002 | User changes workspace ID in request | Membership verified; no data leak |
| SEC-003 | User requests another workspace's agent | Denied or safe not-found |
| SEC-004 | User requests another workspace's logs | Denied |
| SEC-005 | Frontend requests provider credential details | Secret never returned |
| SEC-006 | API key is written to a log | Secret redaction test fails the build |
| SEC-007 | Password is stored | Only password hash stored |
| SEC-008 | Protected endpoint called without auth | Rejected |
| SEC-009 | Insufficient workspace role attempts mutation | Rejected |
| SEC-010 | Tool receives malformed input | Schema validation rejects it |
| SEC-011 | Tool output contains prompt injection | Treated as untrusted data |
| SEC-012 | Execution exceeds configured limit | Stopped or failed safely |
| SEC-013 | Arbitrary Java code execution attempted | No such execution path exists |
| SEC-014 | Request/execution rate exceeded | Rate/concurrency controls apply |
| SEC-015 | Tool targets loopback, private, link-local, multicast or metadata address | Request is blocked before connection |
| SEC-016 | Allowed tool endpoint redirects to a forbidden host | Redirect is blocked |
| SEC-017 | MVP attempts a sensitive write action before approval nodes ship | Tool is unavailable or action is rejected |

## 5. Workflow engine tests
Verify allowed transitions:
- `PENDING → RUNNING`
- `PENDING → CANCELLED`
- `RUNNING → COMPLETED`
- `RUNNING → FAILED`
- `RUNNING → CANCELLED`
- P1: `RUNNING → WAITING_FOR_APPROVAL`
- P1: `WAITING_FOR_APPROVAL → RUNNING`
- P1: `WAITING_FOR_APPROVAL → CANCELLED`

Reject invalid transitions. Also verify:
- State transitions persist.
- Node attempts are recorded separately.
- An uncertain external action sets node status to `OUTCOME_UNKNOWN` and blocks automatic retry.
- Logs include timestamps and event types.
- Workflow version and its agent settings snapshot are fixed for an execution.
- Duplicate idempotency keys create no duplicate execution when the request hash matches; a different hash returns conflict.
- Restart does not erase completed history.
- Restart recovers pending work and handles expired leases according to the documented at-least-once policy.
- Cancellation is persisted and takes effect at a safe node boundary.

## 6. Frontend usability checks
- User can understand how to create the first agent.
- Required fields are clear.
- Nodes can be added, moved, configured and connected.
- Invalid connections are explained.
- Saving/reloading preserves the workflow.
- Running workflows show clear progress/status.
- Failed nodes are identifiable.
- Logs and errors are readable.
- Empty states explain the next action.
- Core experience works at common laptop widths.

## 7. Performance and resilience
Establish measured baselines rather than inventing performance targets. Test paginated history, large log sets, bounded concurrent executions, slow/unavailable providers, database interruption, queue interruption if applicable, large permitted inputs, cancellation during a slow node and application/worker restart.

Record API latency, workflow/node duration, provider latency, failure/retry rates, token usage and estimated cost.

## 8. Test environments and data
- **Local:** Docker Compose, local PostgreSQL, mocked provider by default.
- **CI:** isolated database and deterministic provider mocks.
- **Staging:** production-like configuration and synthetic data.
- **Production:** real user data, strict access controls and monitoring.

Never use production API keys in tests or real user data in fixtures. Include fixtures for two separate users/workspaces, different workspace roles, a mocked agent, a successful workflow, a known failing workflow and, for P1 coverage, an approval workflow.

## 9. CI quality gates
A pull request should pass:
1. Backend compilation.
2. Backend unit tests.
3. Relevant backend integration tests.
4. Frontend type checking.
5. Frontend linting and tests.
6. Production frontend build.
7. Secret scanning or equivalent checks.

Do not merge changes that weaken tenant isolation or expose credentials.

## 10. Private beta acceptance checklist
- [ ] Registration and login work.
- [ ] Workspace access is enforced.
- [ ] Provider secrets are encrypted and never returned to client.
- [ ] Agent creation and testing work.
- [ ] Workflow creation and validation work.
- [ ] Sequential execution works.
- [ ] Execution state and logs persist.
- [ ] Failure handling is understandable.
- [ ] Retry is safe for supported node types.
- [ ] Unknown external outcomes are visible and never retried automatically.
- [ ] Worker restart recovers queued work without blindly replaying side effects.
- [ ] Published workflow versions include stable agent settings snapshots.
- [ ] Tool egress blocks internal and metadata destinations.
- [ ] Cancellation takes effect at a safe node boundary.
- [ ] Cross-workspace security tests pass.
- [ ] Database constraints reject cross-workspace references.
- [ ] Migrations work from a clean database.
- [ ] Backup and restore have been tested.
- [ ] Deployment is documented.
- [ ] Known limitations are documented.

## 11. Test report template

```text
Test run:
Date:
Commit:
Environment:

Commands executed:
- ...

Results:
- Passed:
- Failed:
- Skipped:

Known issues:
- ...

Security checks:
- ...

Release decision:
- Ready / Not ready

Reviewer:
```

Never report a test as passed unless it was actually executed and its result inspected.
