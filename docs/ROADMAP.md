# Development Roadmap

## 1. Delivery strategy

Build a working end-to-end product in small increments. The first milestone is not a large collection of screens; it is one reliable workflow that a user can create, execute, inspect, and debug.

Recommended approach:
- Modular monolith first.
- One backend and one frontend repository workspace.
- PostgreSQL as the source of truth.
- Add Redis and worker infrastructure only when execution needs justify them.
- Keep each milestone deployable and demonstrable.

The 8–12 week outline below is an estimate for a focused solo or small-team build. Actual duration depends on experience, available time, and integration complexity.

## 2. Milestones

### Milestone 0 — Product and repository foundation

**Deliverables**
- Confirm product name and domain strategy.
- Create Git repository and monorepo folders.
- Add README, contribution notes and environment example.
- Create issue board and milestone labels.
- Record architecture decisions.

**Done when**
- A new developer can clone the repository and understand how to start the project.
- Secrets are excluded from Git.

### Milestone 1 — Java execution-engine prototype

**Deliverables**
- Java project with Maven.
- Core domain types: Agent, Workflow, WorkflowNode, ExecutionContext.
- Initial node types: AgentNode, ToolNode, OutputNode.
- In-memory sequential workflow runner.
- Unit tests for successful execution and failure handling.

**Done when**
- A sample workflow runs from start to finish without Spring Boot or a database.
- Node results and errors are represented consistently.

### Milestone 2 — Spring Boot API foundation

**Deliverables**
- Spring Boot application.
- Health endpoint and global exception handling.
- DTO validation.
- OpenAPI documentation.
- Configuration profiles for local and test environments.

**Done when**
- The backend starts locally and exposes documented endpoints.
- Invalid requests return consistent error responses.

### Milestone 3 — PostgreSQL and persistence

**Deliverables**
- PostgreSQL 16 for local development: the native Windows service (`postgresql-x64-16`) with role `meld` and databases `meld` / `meld_test`. `postgres:16` container configuration for CI and production parity (`ARCHITECTURE.md` §13).
- Flyway migrations.
- JPA entities and repositories.
- Workspace, agent and workflow persistence.
- Repository and integration tests against a real PostgreSQL 16 instance
  (`meld_test` locally; Testcontainers where Docker is available).

**Done when**
- Data survives application restarts.
- Migrations run from an empty database.
- Core queries are covered by tests.

### Milestone 4 — Authentication and workspace security

**Deliverables**
- Registration and login.
- Password hashing (BCrypt, cost 12).
- Session-based authentication: HTTP-only `MELD_SESSION` cookie, session-id
  rotation on login, JSON `401`/`403` responses.
- CSRF protection on all state-changing requests (cookie double-submit with a
  `GET /auth/csrf` bootstrap for the SPA).
- Workspace creation and membership, with roles `OWNER` and `MEMBER`.
- Role checks and tenant-scoped service methods (`WorkspaceAccessService` is
  the single authorization seam).
- Case-insensitive email uniqueness and email normalization at the API
  boundary.

**Done when**
- Protected endpoints reject unauthenticated requests.
- A user cannot read or modify another workspace's records.
- Cross-workspace authorization tests pass.

**Not in M4** (deliberately deferred): email verification, password reset,
email invitations, login rate limiting/lockout, workspace deletion, and audit
logging of pre-workspace auth events.

### Milestone 5 — AI provider and agent management

**Deliverables**
- Provider adapter interface, with an OpenAI-compatible chat-completions
  implementation used for OpenRouter first (the free
  `meta-llama/llama-3.1-8b-instruct:free` model) and reusable for OpenAI, Groq
  and Ollama base URLs.
- Secure provider credential storage: AES-256-GCM at rest, key loaded from the
  environment, secrets write-only at the API.
- Provider credential management (`OWNER` creates and deletes; members list).
- Agent create, edit, list, delete and test APIs.
- Prompt and model configuration (model, system prompt, temperature, max
  tokens).
- Token usage and latency capture on test prompts.
- Provider timeouts and safe error mapping.

**Done when**
- A user can configure a provider, create an agent and run a test prompt.
- Provider errors and timeouts are handled safely.
- Secrets never appear in frontend responses or logs.

**Not in M5** (deliberately deferred):
- Tool runtime and egress enforcement. No tool execution path exists yet, so
  host allowlists and address blocking move to M6 together with tool
  invocation.
- Persisting usage records: `usage_records` is execution-scoped (M6).
- Prompt versioning, streaming output, retries/fallback, and adapters for
  non-OpenAI-compatible providers.

### Milestone 6 — Workflow definition and execution

**Deliverables**
- Workflow CRUD.
- Node and edge validation, including database constraints that keep edges inside one workflow version.
- Immutable workflow versions with snapshots of the referenced agents' non-secret settings.
- Sequential execution engine.
- Execution and node-execution persistence.
- PostgreSQL-backed durable work queue with worker leases, heartbeats, and recovery of expired claims.
- Request idempotency semantics and stable invocation keys for retryable tool calls.
- Basic timeout handling and safe handling of unknown external-call outcomes.
- Tool execution with default-deny egress: explicit host allowlists, blocking
  of loopback/private/link-local/multicast/metadata addresses, a pinned DNS
  result, and redirect revalidation. MVP tools stay read-only or low-risk.

**Done when**
- A saved workflow can be executed against an immutable version whose agent prompts and model settings do not change when reusable agents are edited.
- Execution status and node outputs can be retrieved after completion.
- Failures are persisted with useful diagnostic details.
- A configured tool can reach only approved destinations and cannot reach
  internal or metadata addresses.
- A worker restart recovers queued work; a non-idempotent call with an unknown outcome is not replayed automatically.

### Milestone 7 — React application and visual builder

**Deliverables**
- React + TypeScript + Vite application.
- Authentication and workspace screens.
- Agent list and Agent Studio.
- Workflow canvas using React Flow / XYFlow.
- Node configuration panels.
- Workflow save and validation.

**Done when**
- A user can create and save a workflow from the UI.
- Reloading the page restores the saved workflow.
- The UI reports validation errors clearly.

### Milestone 8 — Execution Center and debugging

**Deliverables**
- Execution history screen.
- Execution detail screen.
- Per-node status and logs.
- Failure display.
- Retry and cooperative cancellation for supported cases.
- Polling or SSE progress updates.

**Done when**
- A user can launch a workflow and follow its progress.
- A failed node has a useful error explanation.
- Retry behavior does not accidentally duplicate unsafe side effects.

### Milestone 9 — Post-MVP advanced nodes

**Deliverables**
- Conditional and human-approval nodes.
- Configurable retry policies and backoff.
- Cancellation propagation during node execution where the provider supports it.

**Done when**
- Paused approvals can be decided by an authorized user.
- Branch and approval behavior is versioned and tested.

### Milestone 10 — Post-MVP integrations and scheduling

**Deliverables**
- Redis-backed queue if justified.
- Scheduled workflow triggers.
- MCP integration for selected tools.
- Tool permission and input validation.
- Webhooks or external triggers.

**Done when**
- Scheduled runs are recorded and observable.
- Tool calls are constrained and audited.
- Integration failures do not corrupt execution state.

### Milestone 11 — Beta hardening and deployment

**Deliverables**
- Dockerfiles and production configuration.
- CI pipeline.
- Unit, integration and end-to-end tests.
- Rate limits and usage quotas.
- Structured logging and basic metrics.
- Backup and restore procedure.
- Security review and deployment documentation.
- Landing page and onboarding guide.

**Done when**
- A fresh environment can be deployed from documented steps.
- Critical user journeys pass automated tests.
- Logs and metrics are sufficient to diagnose common failures.
- Secrets and user data are protected.

## 3. Suggested 12-week schedule

| Week | Focus | Demonstrable result |
|---:|---|---|
| 1 | Repository + Java engine prototype | Sequential workflow runs in tests |
| 2 | Spring Boot API | Running API with validation |
| 3 | PostgreSQL + Flyway | Persistent workspaces and agents |
| 4 | Authentication + tenant isolation | Secure workspace APIs |
| 5 | AI provider + agent studio API | Agent can call a model |
| 6 | Workflow definitions + engine | Saved workflow executes |
| 7 | React foundation + visual builder | Workflow can be created visually |
| 8 | Execution Center | Status, outputs and logs visible |
| 9 | Retry and cooperative cancellation | Recoverable core execution lifecycle |
| 10 | Security, recovery and integration tests | Beta-quality core flows |
| 11 | Deployment, docs and observability | Deployable private beta |
| 12 | User feedback and fixes | Private beta learning loop |

## 4. MVP priority checklist

### Must have
- [x] Register and sign in.
- [x] Create a workspace.
- [x] Configure one AI provider securely.
- [ ] Restrict tools to approved destinations and read-only or low-risk actions.
- [x] Create and test an agent.
- [ ] Create and save a workflow.
- [ ] Execute sequential nodes.
- [ ] Persist execution and node status.
- [ ] View execution history and logs.
- [ ] Explain failures and support safe retries.
- [ ] Cancel a workflow at a safe node boundary.
- [x] Enforce workspace-level authorization.

### Should have
- [ ] Workflow version history in the UI (immutable versions are required for the MVP).
- [ ] Conditional branching.
- [ ] Token and cost estimates.
- [ ] Live progress updates.

### Later
- [ ] Multiple AI providers.
- [ ] MCP integrations.
- [ ] Scheduled workflows.
- [ ] Human approval nodes.
- [ ] Parallel execution.
- [ ] Team roles and organization management.
- [ ] Billing, quotas and analytics.
- [ ] Public API and webhooks.

## 5. Testing and release gates

Before each milestone is considered complete:
1. Run formatter and static checks.
2. Run unit tests.
3. Run relevant integration tests.
4. Verify authorization boundaries.
5. Test expected failure paths.
6. Update API and setup documentation.
7. Demonstrate the milestone from a clean local environment.

Before private beta:
- Test registration through workflow execution.
- Test provider timeout and invalid credentials.
- Test database restart and execution recovery.
- Test cross-workspace access attempts.
- Verify secrets are absent from browser responses and logs.
- Restore a database backup in a test environment.

## 6. Product validation

Before investing heavily in commercial features:
- Interview 5–10 developers or small teams who build AI workflows.
- Ask how they currently orchestrate agents and tools.
- Observe how they debug failed runs.
- Identify which integrations they actually need.
- Give users a working prototype and watch them use it.
- Track first-workflow success, repeat usage, completion rate and execution cost.

Avoid assuming pricing or demand before validating with real users.

## 7. Scope control

If time becomes limited, prioritize this single complete journey:

**Sign in → Create agent → Build a two- or three-node workflow → Execute → Inspect logs → Fix and retry.**

A polished, reliable end-to-end journey is more valuable than many unfinished features.
