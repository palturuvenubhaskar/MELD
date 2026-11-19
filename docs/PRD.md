# Product Requirements Document (PRD)

## 1. Product overview

**Working product name:** AI Workflow Orchestration Platform  
**Tagline:** Orchestrate Intelligence. Automate Everything.  
**Product type:** Developer-first SaaS platform for building, running, and monitoring AI agents and automated workflows.

The platform lets developers and small teams connect AI agents, tools, APIs, and decision steps into repeatable workflows. Users should be able to design workflows visually, execute them reliably, and inspect every step. Human approval for sensitive actions is a planned P1 expansion.

The product should begin as a modular monolith. Avoid premature microservices.

## 2. Problem statement

Developers currently assemble AI workflows using disconnected scripts, provider dashboards, automation tools, and custom infrastructure. This makes workflows difficult to inspect, reproduce, debug, secure, and maintain.

The product aims to provide one place to:
- Configure reusable AI agents.
- Connect approved tools and external services.
- Build workflows from visual nodes.
- Execute workflows with durable state.
- Inspect logs, failures, retries, and costs.
- Add human approval for sensitive actions in a later expansion.

## 3. Target users

### Primary
- Software developers building AI-powered products.
- Small engineering teams and startups.
- Technical founders prototyping AI automation.

### Secondary
- Operations teams with technical support.
- Product and automation teams.

## 4. Product goals

1. Make a basic AI workflow possible without writing orchestration code.
2. Give developers visibility into every workflow execution.
3. Support reusable agents, tools, and workflow templates.
4. Make failures recoverable through retries and clear error details.
5. Keep user data and provider credentials isolated and protected.
6. Establish a foundation for team and commercial features.

## 5. Non-goals for the first release

- General-purpose no-code business automation.
- Arbitrary execution of user-supplied Java code on the application server.
- A full marketplace for third-party agents.
- Microservices from day one.
- Enterprise billing and complex organization management in the MVP.

## 6. Core user journeys

### Journey A: Create an agent
1. User signs in.
2. User opens the Agents section.
3. User creates an agent with a name, description, system instructions, and model provider.
4. User optionally attaches permitted tools.
5. User tests the agent.
6. User saves it for reuse.

### Journey B: Build a workflow
1. User creates a workflow.
2. User adds nodes to a visual canvas.
3. User connects nodes in execution order.
4. User configures each node.
5. User validates and saves the workflow.
6. User runs a test execution.

### Journey C: Inspect an execution
1. User opens the Execution Center.
2. User selects an execution.
3. User sees overall status and per-node status.
4. User opens logs and input/output details.
5. User retries an eligible failed node or requests cancellation, which takes effect at a safe node boundary.

### Future journey (P1): Human approval
1. A workflow reaches an approval node.
2. Execution pauses in `WAITING_FOR_APPROVAL`.
3. An authorized user reviews the pending action and context.
4. User approves or rejects it.
5. Execution resumes or terminates according to the workflow definition.

## 7. Functional requirements

### P0 — MVP

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-001 | User authentication | Users can register, sign in, and sign out securely. |
| FR-002 | Workspace | A user can create and access a workspace. |
| FR-003 | Agent management | Users can create, view, update, and delete agents. |
| FR-004 | Model provider configuration | Users can configure a supported AI provider without exposing secrets to the browser. |
| FR-005 | Prompt editor | Users can edit system instructions and test an agent. |
| FR-006 | Tool registry | Users can register and manage approved tools. |
| FR-007 | Visual workflow builder | Users can add, configure, connect, and save workflow nodes. |
| FR-008 | Sequential execution | A workflow can execute connected nodes in order. |
| FR-009 | Execution status | Users can see pending, running, completed, failed, and cancelled states. |
| FR-010 | Execution history | Users can list and open previous executions. |
| FR-011 | Logs | Each execution and node execution records useful logs and errors. |
| FR-012 | Retry | A failed execution or node can be retried when its side effects are safe to repeat; uncertain non-idempotent calls require an operator decision. |

### P1 — Expansion

- Multiple agents collaborating in one workflow.
- Conditional branching.
- Parallel node execution.
- Scheduled workflows.
- MCP server/tool integrations.
- Human approval nodes.
- Configurable retry policies and backoff.
- Workflow templates.
- Token and estimated cost tracking.
- Live execution updates in the UI.

### P2 — Commercial

- Organizations and team workspaces.
- Role-based access control.
- Subscription plans and usage quotas.
- API keys and public API access.
- Audit logs and organization analytics.
- Webhooks and custom integrations.
- Shared templates and team collaboration.

## 8. Workflow node types

Initial node types:
- **AgentNode:** invokes a configured AI agent.
- **ToolNode:** invokes an approved tool; MVP tools are read-only or low-risk.
- **ConditionNode:** selects a path based on a defined condition.
- **DelayNode:** waits for a configured duration.
- **ApprovalNode:** pauses for a human decision.
- **OutputNode:** stores or presents the final result.

For the MVP, implement Agent, Tool, and Output nodes. Condition, Delay, and Approval nodes are P1 and are not required for the first beta.

## 9. Execution lifecycle

MVP execution states:

`PENDING → RUNNING → COMPLETED`

Other MVP transitions:
- `PENDING → CANCELLED`
- `RUNNING → FAILED`
- `RUNNING → CANCELLED`

P1 approval transitions:
- `RUNNING → WAITING_FOR_APPROVAL`
- `WAITING_FOR_APPROVAL → RUNNING`
- `WAITING_FOR_APPROVAL → CANCELLED`

Persist execution and node state so restarts do not erase history or strand work. Workers claim durable pending executions with expiring leases and heartbeats; expired work is recovered after a restart. Execution is at-least-once, so an external action may have happened even if its result was not saved. In that case, mark the node `OUTCOME_UNKNOWN`, fail the execution, and require an operator to resolve the outcome before retrying it.

## 10. Non-functional requirements

### Security
- Hash passwords using a modern password-hashing algorithm.
- Encrypt provider API keys at rest.
- Never send provider secrets to the frontend.
- Check workspace membership and permissions on every protected request.
- Validate and restrict tool inputs and outputs.
- Run network-capable tools with outbound access denied by default; allow only explicitly approved destinations, reject loopback/private/link-local/multicast and metadata addresses, pin a vetted DNS result for the connection, and revalidate every redirect target.
- Restrict MVP tools to read-only or low-risk actions. Sensitive actions require an approval node, which is P1.
- Defend against prompt injection and malicious tool output.
- Apply request, token, execution-time, and tool-call limits.
- Do not execute arbitrary Java code in the application process.

### Reliability
- Persist execution state.
- Hash the canonical workflow ID, version ID, and normalized input for execution request idempotency. For a repeated key, return the existing execution only when that hash matches; return a conflict if the key is reused for a different request.
- Treat tool calls as at-least-once. Reuse a stable invocation key for a retried logical action and pass it to tools that support idempotency.
- Never automatically retry a non-idempotent call whose outcome is unknown; show the uncertainty and require an operator decision.
- Record structured errors and timestamps.
- Persist cancellation requests; cancel pending runs immediately and running runs at a safe node boundary. Do not imply that an in-flight external action was reversed.
- Make retry behavior explicit.
- Recover expired worker leases after interruption and persist every state transition.

### Performance and usability
- Paginate execution history and logs.
- Avoid blocking HTTP requests for long-running workflows.
- Show clear progress and actionable errors.
- Keep the visual builder usable on common laptop screen sizes.

### Maintainability
- Use clear module boundaries.
- Add automated tests for core business rules.
- Use database migrations.
- Document API contracts and architecture decisions.

## 11. Success metrics

For an early private beta, measure:
- Time from registration to first successful workflow.
- Percentage of users completing their first workflow.
- Workflow completion and failure rates.
- Number of workflows executed per active workspace.
- Seven-day returning workspace rate.
- Average execution duration and cost.
- Retry success rate.

Do not treat target values as validated until baseline usage data exists.

## 12. MVP release definition

The MVP is ready for a controlled beta when a user can:
1. Register and sign in.
2. Create a workspace.
3. Configure an AI provider securely.
4. Create and test an agent.
5. Create a workflow with supported nodes.
6. Execute the workflow sequentially.
7. View execution status, node details, and logs.
8. Understand failures and retry eligible work.
9. Use the application without accessing another workspace's data.
10. Request cancellation and see it take effect at a safe node boundary.
11. See ambiguous external action outcomes and resolve them before retrying.

## 13. Open product decisions

Track these decisions before implementation:
- Which AI providers are supported first? — **Decided:** OpenRouter through the
  OpenAI-compatible adapter, default model
  `meta-llama/llama-3.1-8b-instruct:free` (`ARCHITECTURE.md` §7).
- Which tools are built in versus user-configured?
- What is the first supported workflow input/output format?
- Should the MVP support streaming model output?
- What execution timeout and token limits should apply?
- Which features are free during private beta?
