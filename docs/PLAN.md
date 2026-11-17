# Implementation Plan

## Goal
Deliver one complete, reliable user journey before expanding scope:

**Sign in → Create agent → Build workflow → Execute → Inspect logs → Retry safely or cancel at a node boundary.**

Start with a modular monolith. Keep the first release small and demonstrable.

## Repository structure

```text
project/
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── DATABASE.md
│   ├── ROADMAP.md
│   ├── PLAN.md
│   ├── RULES.md
│   └── TEST.md
├── backend/
├── frontend/
├── infrastructure/
├── .env.example
├── .gitignore
└── README.md
```

## Workstreams

### 1. Backend foundation
- Create the Java/Spring Boot Maven project.
- Add configuration profiles, health endpoint and global exception handling.
- Add DTO validation and API documentation.
- Configure PostgreSQL and Flyway.
- Establish unit and integration test conventions.

### 2. Domain and execution engine
- Define Agent, Workflow, WorkflowNode, ExecutionContext and result types.
- Define explicit execution and node state machines.
- Validate workflow nodes and edges.
- Make normalized node and edge rows the canonical workflow definition; publish immutable versions and snapshot non-secret agent settings into each version.
- Implement sequential execution first.
- Use the PostgreSQL execution row as a durable queue item, with worker leases, heartbeats and recovery of expired work.
- Define request idempotency using workflow ID, version ID and normalized input; same key with different input is a conflict.
- Treat tool calls as at-least-once and use a stable invocation key for retries when the tool supports idempotency.
- Mark uncertain non-idempotent calls `OUTCOME_UNKNOWN`; require operator resolution before retrying.
- Persist cancellation requests and cancel at safe node boundaries.
- Add retries only after basic execution is reliable.

### 3. Authentication and workspaces
- Implement registration and login.
- Hash passwords securely.
- Create workspace and membership services.
- Implement roles and authorization checks.
- Scope every workspace-owned read and write.
- Enforce workspace ownership in database foreign keys for credentials, agent/tool links, workflows and executions.
- Add cross-workspace access tests.

### 4. Agents and AI providers
- Define a provider adapter interface.
- Integrate one provider first.
- Encrypt credentials on the backend.
- Implement agent create, update, list and test operations.
- Capture provider errors and usage metadata.
- Add further providers only after the adapter is stable.

### 5. Tools and integrations
- Define tool metadata and input schemas.
- Implement a few read-only or low-risk built-in tools; keep sensitive actions disabled until P1 approval nodes ship.
- Validate tool inputs and outputs.
- Deny outbound network access by default; allow approved destinations only and block local, private, link-local, multicast and metadata addresses.
- Add permissions, timeouts and audit logs.
- Add MCP integrations in a later milestone.

### 6. Frontend
- Set up React, TypeScript and Vite.
- Implement authentication and workspace selection.
- Build agent list and agent editor.
- Build workflow canvas using React Flow / XYFlow.
- Add node configuration and workflow validation.
- Add execution history, detail and log screens.

### 7. Delivery
- Add Docker Compose for CI and production services. Local development uses the native PostgreSQL 16 service (`ARCHITECTURE.md` §13); compose must pin `postgres:16` with the same role and database names so the two environments stay interchangeable.
- Add CI for formatting, tests and builds.
- Add structured logging and basic metrics.
- Document secrets, backups and deployment.
- Deploy a private beta and collect feedback.

## Implementation order

| Step | Deliverable | Exit condition |
|---:|---|---|
| 1 | Repository and docs | Setup is documented |
| 2 | Java engine prototype | Sequential engine tests pass |
| 3 | Spring Boot API | API starts and validates requests |
| 4 | PostgreSQL + Flyway | Data persists across restarts; tenant and workflow ownership constraints are enforced |
| 5 | Auth + workspaces | Tenant isolation tests pass |
| 6 | Provider + agent APIs | Agent test call succeeds |
| 7 | Workflow persistence | Immutable version includes node/edge data and agent settings snapshot |
| 8 | Execution engine | Durable execution survives worker restart without unsafe replay |
| 9 | Frontend builder | User can create workflow visually |
| 10 | Execution Center | User can inspect outputs and logs |
| 11 | Reliability | Recovery, safe retry, cancellation and unknown outcomes handled |
| 12 | Private beta | Critical journeys pass in a clean deployment |

## Definition of ready
A task is ready when its user problem, expected behavior, acceptance criteria, API/data impact, security implications and dependencies are understood.

## Definition of done
- Acceptance criteria are met.
- Validation and error handling exist.
- Relevant tests pass.
- Authorization is enforced.
- No secrets are exposed.
- Documentation is updated.
- Build succeeds.
- Changes are reviewed.

## AI coding-agent workflow
For each task, provide the agent with the relevant PRD, architecture/database sections, exact task, acceptance criteria, `RULES.md` and relevant tests from `TEST.md`.

Require it to:
1. Inspect existing code before editing.
2. State a short plan for multi-file changes.
3. Make focused changes only.
4. Run relevant tests and builds.
5. Inspect its diff.
6. Report files changed, commands run, results and limitations.

Manually review authentication, authorization, credential handling, migrations and execution safety.

## Risk-first priorities
Address cross-workspace data access, leaked credentials, duplicate execution, unsafe retries, lost execution state, unbounded model/tool calls and prompt injection early.

## Change management
When requirements change, update the PRD, architecture/schema if needed, this plan, and test cases. Add a migration for schema changes and record important architectural decisions.
