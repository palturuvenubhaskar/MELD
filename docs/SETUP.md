# Setup and first-run guide

How to pick this project up from a clean machine state or a fresh agent
session. Read together with `AGENTS.md` (mandatory reading protocol) and
`PLAN.md` (implementation order).

## 1. What is already in place

| Item | Value |
|---|---|
| Repository root | `V:\PROJECTS\MELD` |
| Branch | `main` |
| Java | Temurin 21.0.13.9 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`), `JAVA_HOME` set at user level |
| Maven | 3.10.1 via scoop (`~\scoop\apps\maven\current\bin`) |
| Node / npm | 24.15.0 / 11.12.1 |
| PostgreSQL | 16.3, native Windows service `postgresql-x64-16`, `localhost:5432` |
| `psql` / `pg_dump` / `pg_restore` | `C:\Program Files\PostgreSQL\16\bin` (on user PATH) |
| Docker | **not installed**, not required — see `ARCHITECTURE.md` §13 |

### Database

| Item | Value |
|---|---|
| Application role | `meld` — `LOGIN` only; `super=false createdb=false createrole=false` |
| Dev database | `meld` (owner `meld`) |
| Test database | `meld_test` (owner `meld`) |
| Auth | `scram-sha-256` for `local`, `127.0.0.1/32`, `::1/128` |
| Dev password | in `.env` (`DB_PASSWORD`); never commit it, never echo it |

The `postgres` superuser password is **not recorded in this repository and is
not known to the agents working here**. Do not attempt to log in as `postgres`;
everything needed is reachable through the `meld` role.

### Environment file

`.env` exists at the repo root and is gitignored. It defines:

```
DB_HOST DB_PORT DB_NAME DB_NAME_TEST DB_USER DB_PASSWORD
DATABASE_URL TEST_DATABASE_URL SPRING_PROFILES_ACTIVE
MELD_CREDENTIAL_ENCRYPTION_KEY   # 32 random bytes, base64 (AES-256-GCM)
```

`.env.example` carries the same keys with placeholders and **is** committed.

## 2. Smoke test — run this before writing code

```powershell
# if your shell predates the PATH changes:
$env:Path = [Environment]::GetEnvironmentVariable('Path','User') + ';' + [Environment]::GetEnvironmentVariable('Path','Machine')
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')

java -version      # expect "21.0.13" LTS, not 19
mvn -v             # expect Maven 3.10.1 on Java 21
node -v            # expect v24.15.0
psql --version     # expect 16.3

# database (password comes from .env; do not paste it into chat)
$env:PGPASSWORD = (Select-String -Path .env -Pattern '^DB_PASSWORD=' ).Line -replace '^DB_PASSWORD=',''
psql -h localhost -U meld -d meld      -c "select current_user, current_database()"
psql -h localhost -U meld -d meld_test -c "select current_user, current_database()"
```

All five must succeed. If `mvn` or `psql` is "not recognized", the shell is
inheriting a stale environment — set the two variables above, or restart the
terminal/agent host.

## 3. Open decisions (needed by phase 4–5, not phase 0–2)

Tracked in `PRD.md` §13. Proposed defaults, not yet ratified:

| Decision | Proposed |
|---|---|
| First provider | OpenAI-compatible adapter (covers OpenAI, Groq, OpenRouter, Ollama) |
| MVP tools | 2 built-ins: allowlisted HTTP GET, JSON transform |
| Workflow I/O | JSON in / JSON out |
| Streaming | No — poll status (`ARCHITECTURE.md` §9) |
| Limits | 120 s/node, 2k output tokens, 20 tool calls, 3 concurrent per workspace |
| Auth transport | HTTP-only session cookie, stateful Spring Security |

Record the decision in the docs when made — do not let it live only in a chat.

## 4. Build order

`ROADMAP.md` fixes the milestones; this is the practical sequence:

1. `backend/` — Spring Boot 3 + Java 21 (web, validation, data-jpa, flyway,
   postgresql, actuator), Maven wrapper committed.
2. `application-local.yml` — bind `${DB_*}` from `.env`; prove Flyway connects
   to `meld`. `.env` is **not** read by Spring automatically; either export the
   variables or map them explicitly. Do not add a dotenv dependency without
   saying why.
3. `V1__baseline.sql` — `users`, `workspaces`, `workspace_members` per
   `DATABASE.md` §5, including the composite keys.
4. `frontend/` — Vite + React 19 + TypeScript + Tailwind, then React Flow for
   the canvas.
5. Engine prototype tests → auth → provider → execution engine (`PLAN.md`).

Milestone 1 in `ROADMAP.md` is the pure-Java engine prototype before Spring
Boot. Doing the scaffold first is an accepted trade (it gives a runnable test
harness), but do not let it delay the engine.

## 5. Traps that bite a fresh session

- **Stale environment.** A long-running terminal or agent host may not see the
  new `JAVA_HOME`/PATH. Fix with the two `$env:` lines in §2.
- **Java 19 still installed.** It loses to `jdk-21` in PATH order, but never
  hard-code a Java path — use `JAVA_HOME`/`mvnw`.
- **`.env` is not Spring configuration.** It is a shell/CI convenience file.
- **No Docker.** Integration tests therefore use `meld_test` on the native
  service. Verify `application-test.yml` can *only* reach `meld_test`; a test
  run must never touch `meld`. Document any change to this in `TEST.md` §8.
- **Secrets.** Never print `.env`, never commit it, never return provider
  credentials or password hashes in any API response.
- **Docs are the contract.** A behavior, schema or scope change means a doc
  change in the same commit. Load the `meld-docs` skill for the map of which
  document governs what, and for the list of rules duplicated across files.
- **`pg_hba.conf.meldbak`** may still exist in `C:\Program Files\PostgreSQL\16\data`
  as a leftover backup from the role bootstrap. It is inert; removing it needs
  an elevated shell.

## 6. Definition of done for phase 0

- [x] Repository, docs, `.gitignore`, `.gitattributes`
- [x] `AGENTS.md` reading protocol + `meld-docs` skill
- [x] Java 21, Maven, Node, PostgreSQL 16, `psql` on PATH
- [x] `meld` role, `meld` / `meld_test` databases, SCRAM verified
- [x] `.env` with real credentials and generated encryption key
- [ ] `backend/` scaffold builds and starts
- [ ] Flyway migration applies to a clean `meld`
- [ ] `frontend/` scaffold builds
