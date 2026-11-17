---
name: MELD Docs
description: Map of the MELD specification in docs/ — which document governs which decision, the reading order for a given task, and every place a rule is duplicated so changes stay in sync. Load before any MELD implementation work.
---

# MELD documentation map

`docs/` is the contract. `AGENTS.md` requires reading all seven files in full
before touching code. This skill tells you *what to look for* when you do, and
*where else a rule lives* so a change does not leave the spec inconsistent.

| File | Governs | Read it when |
|---|---|---|
| `PRD.md` | Product scope, P0/P1/P2 tiers, user journeys, execution lifecycle, non-functional requirements, MVP release definition | Deciding whether something is in scope; any requirement question |
| `ARCHITECTURE.md` | Stack, module boundaries, Java contracts, API surface, execution design, real-time strategy, deployment topology | Before creating any module, endpoint, or abstraction |
| `DATABASE.md` | Entity model, SQL for every table, indexes, tenant-isolation rules, data lifecycle | Before any entity, repository, or migration |
| `ROADMAP.md` | Milestones 0–11 and their "done when" gates, 12-week schedule, must/should/later checklist | Choosing what to build next; declaring a milestone complete |
| `PLAN.md` | Repository layout, seven workstreams, the 12-step implementation order, definition of done | Estimating or sequencing work |
| `RULES.md` | Hard engineering rules for architecture, Java, DB, API, security, workflow engine, frontend, testing, git | Before writing code in any layer — this is the enforcement doc |
| `TEST.md` | TC-001…TC-032, SEC-001…SEC-017, state-machine checks, CI quality gates, beta acceptance checklist | Writing tests for any new behavior |

## Reading order by task type

- **Auth / workspace** → `DATABASE.md` §5 (users, workspaces, memberships), §7 → `RULES.md` Security → `TEST.md` SEC-001…SEC-009.
- **Agent / provider credential** → `ARCHITECTURE.md` §4, §11 → `DATABASE.md` §5 (provider_credentials, agents) → `PRD.md` §6 Journey A → `TEST.md` TC-007…TC-010.
- **Workflow definition / versioning** → `ARCHITECTURE.md` §8 → `DATABASE.md` §5 (workflows, versions, nodes, edges) → `ROADMAP.md` M6 → `TEST.md` TC-011…TC-013, TC-023, TC-032.
- **Execution engine / queue / recovery** → `ARCHITECTURE.md` §5, §8, §10 → `PRD.md` §9, §10 Reliability → `DATABASE.md` §5 (executions, node_executions), §6 → `TEST.md` §5, TC-014…TC-022, TC-027…TC-030.
- **Tools / egress / MCP** → `PRD.md` §10 Security → `ARCHITECTURE.md` §11 → `PLAN.md` §5 → `RULES.md` Security → `TEST.md` SEC-010…SEC-017.
- **Frontend / canvas** → `ARCHITECTURE.md` §6 → `PRD.md` §6 Journeys A–C → `RULES.md` Frontend → `TEST.md` §6, TC-011.
- **Schema change** → `DATABASE.md` §2, §5 for the affected table → `ROADMAP.md` M3 → `RULES.md` Database. Then write the migration and the constraint tests.
- **Milestone sign-off** → `ROADMAP.md` §5 release gates + the milestone's own "Done when" + `TEST.md` §9 CI gates.

## Rules that live in more than one document

A change to one of these **must** be applied to every copy below, or the spec
drifts. This is the most common source of inconsistency in this repo.

| Rule | Duplicated in |
|---|---|
| Provider credentials encrypted at rest, never to browser/logs | `PRD.md` §10, `ARCHITECTURE.md` §11, `DATABASE.md` §5, `RULES.md` Security, `TEST.md` SEC-005/006 |
| Tenant isolation + composite workspace foreign keys | `PRD.md` §10, `ARCHITECTURE.md` §5/§11, `DATABASE.md` §6/§7, `RULES.md` Database, `PLAN.md` §3, `TEST.md` TC-031, SEC-001…004 |
| Immutable versions + snapshot of non-secret agent settings | `ARCHITECTURE.md` §5/§8, `DATABASE.md` §5, `RULES.md` Database/Workflow engine, `PLAN.md` step 7, `ROADMAP.md` M6, `TEST.md` TC-023 |
| At-least-once calls, stable invocation key, `OUTCOME_UNKNOWN` blocks retry | `PRD.md` §9/§10, `ARCHITECTURE.md` §10, `DATABASE.md` §5/§6, `RULES.md` Workflow engine, `PLAN.md` step 2, `ROADMAP.md` M6, `TEST.md` TC-021/TC-030, §5 |
| Request idempotency = hash(workflow, version, normalized input); mismatch → conflict | `PRD.md` §10, `ARCHITECTURE.md` §8, `DATABASE.md` §6, `RULES.md` Workflow engine, `ROADMAP.md` M6, `TEST.md` TC-027/028 |
| Cooperative cancellation at a safe node boundary | `PRD.md` §9/§10, `ARCHITECTURE.md` §10, `DATABASE.md` §5 (`cancel_requested_at`), `RULES.md`, `TEST.md` TC-022 |
| Default-deny egress, block private/link-local/metadata, pin DNS, revalidate redirects | `PRD.md` §10, `ARCHITECTURE.md` §11, `PLAN.md` §5, `ROADMAP.md` M5, `RULES.md` Security, `TEST.md` SEC-015/016 |
| PostgreSQL is authoritative for workflow/execution state | `ARCHITECTURE.md` §10, `DATABASE.md` §1/§6, `RULES.md` Database |

## Decisions the docs deliberately leave open

Do not silently pick one — decide, then record it in the docs (`PRD.md` §13
tracks these). As of the current docs, unspecified:

- First AI provider (and whether to use an OpenAI-compatible adapter).
- Built-in vs user-configured tools for the MVP set.
- Workflow input/output **mapping** format — `PRD.md` §13 lists it as open; the
  node `configuration` JSONB shape per node type is not defined anywhere.
- Streaming model output in the MVP (assume no; polling per `ARCHITECTURE.md` §9).
- Concrete timeout, token, tool-call and rate limits.
- Authentication transport: session cookie vs JWT — `ROADMAP.md` M4 says
  "session or token", `DATABASE.md` has no token/session table.
- Free-tier scope during private beta.

## Before you claim a doc is satisfied

Cheap checks that catch most drift:

```powershell
# every P0 requirement should have a test case id
Select-String -Path docs\PRD.md -Pattern 'FR-0' | Measure-Object

# every duplicated rule table above should still agree
Select-String -Path docs\*.md -Pattern 'OUTCOME_UNKNOWN'
Select-String -Path docs\*.md -Pattern 'immutable'
Select-String -Path docs\*.md -Pattern 'FOR UPDATE SKIP LOCKED'
```

If you change a rule, update **all** hits, and re-read the affected table above.
