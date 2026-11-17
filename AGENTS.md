# MELD — agent instructions

MELD is an AI workflow orchestration platform: developers configure reusable
**agents**, compose them into **workflows** on a visual canvas, execute them
durably, and inspect every step in an **Execution Center**.

Stack (fixed by `docs/ARCHITECTURE.md`): Java 21 + Spring Boot 3 + Maven,
PostgreSQL + Flyway, React 19 + TypeScript + Vite + Tailwind + React Flow
(`@xyflow/react`). Modular monolith. This repository is at **phase 0** —
docs only, no code yet.

---

## MANDATORY session-start protocol

Before you write, edit, or review **any** code in this repository:

1. **Read every file in `docs/` in full** — `PRD.md`, `ARCHITECTURE.md`,
   `DATABASE.md`, `PLAN.md`, `ROADMAP.md`, `RULES.md`, `TEST.md`.
   Do not skim. Do not rely on a summary from a previous session.
2. If you need the map of which document governs which decision, load the
   **`meld-docs`** skill (`skill` tool, id `meld-docs`) and follow its
   reading order for the task type.
3. State the phase and the doc sections that justify your change before
   editing. If the docs contradict each other, say so instead of picking one.

Only exception: a purely conversational question that touches no code and no
files. Even then, check the relevant doc before asserting a requirement.

---

## Non-negotiables (condensed from `docs/RULES.md`)

- **Tenant isolation is a database contract, not just a service check.** Every
  workspace-owned row carries `workspace_id`; cross-workspace relationships are
  rejected by composite foreign keys.
- **Never** send provider secrets, password hashes, or stack traces to the
  browser or into logs. Provider credentials are encrypted at rest; the key
  lives outside PostgreSQL and outside git.
- **Published workflow versions are immutable.** Edits create a new version.
  Agent nodes snapshot non-secret agent settings (provider, model, prompt,
  config) and keep only a workspace-scoped credential *reference*.
- **PostgreSQL is the queue and the source of truth.** Claim with
  `FOR UPDATE SKIP LOCKED` + expiring lease + heartbeat. Redis is never
  authoritative for execution history.
- **Execution is at-least-once.** Use a stable invocation key per logical node
  action. An uncertain non-idempotent call becomes `OUTCOME_UNKNOWN` — never
  auto-retried, never silently replayed.
- **Cancellation is cooperative** and takes effect at a node boundary. Never
  claim an in-flight side effect was reversed.
- **Migrations:** Flyway only, never edit an applied migration, never rely on
  Hibernate auto-DDL.
- **No arbitrary user-supplied Java execution** in the backend JVM.
- **MVP tools are read-only / low-risk**, egress-denied by default with explicit
  allowlists; block loopback, private, link-local, multicast and metadata
  addresses, pin a vetted DNS result, revalidate every redirect.
- Every new business behavior and every new workspace-scoped endpoint needs a
  test, including the cross-tenant denial case (`docs/TEST.md`).

---

## Working rules

- Inspect existing code before editing. Do not invent APIs.
- Keep changes focused. Do not refactor unrelated code.
- Run the relevant build and tests, inspect the diff, and report the exact
  commands and results. **Never report a test as passed unless it ran.**
- Update the docs when behavior, schema, or scope changes — the docs are the
  contract, not an artifact.
- Do not add a dependency without stating why.

---

## Environment

- `JAVA_HOME` → Temurin 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`)
- Maven 3.10.1 (`mvn` via scoop), plus a Maven wrapper once `backend/` exists
- Node 24.15 / npm 11.12
- Docker Desktop: **not yet installed** (needs WSL2 + reboot) — PostgreSQL and
  Testcontainers are blocked until it is.
