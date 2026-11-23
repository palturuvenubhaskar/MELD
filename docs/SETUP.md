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

### Running the backend test suite

`application-test.yml` intentionally contains no password. Export `.env` into
the shell first, then run the wrapper:

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } |
    ForEach-Object { $k,$v = $_ -split '=',2; Set-Item -Path "env:$k" -Value $v }
cd backend
./mvnw test
```

Tests resolve `meld_test` only; the build fails fast if `DB_PASSWORD` is unset.
Persistence tests are transactional and roll back, so rows do not accumulate.

### API smoke test (M4)

Registration does not create a session; log in afterwards. Browser clients must
send the CSRF token from `GET /auth/csrf` in the `X-XSRF-TOKEN` header
(cookie double-submit):

```powershell
$base = 'http://localhost:8080/api/v1'
$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$csrf = Invoke-RestMethod "$base/auth/csrf" -WebSession $s
$h = @{ 'X-XSRF-TOKEN' = $csrf.token }
$body = @{ email = 'dev@example.test'; password = 'correct-horse-battery'; displayName = 'Dev' } | ConvertTo-Json

Invoke-RestMethod "$base/auth/register" -Method Post -WebSession $s -Headers $h -ContentType 'application/json' -Body $body
Invoke-RestMethod "$base/auth/login"    -Method Post -WebSession $s -Headers $h -ContentType 'application/json' -Body $body
Invoke-RestMethod "$base/workspaces"    -Method Post -WebSession $s -Headers $h -ContentType 'application/json' -Body (@{ name = 'Demo Workspace' } | ConvertTo-Json)
Invoke-RestMethod "$base/workspaces"    -WebSession $s
```

### Provider credentials and agents (M5)

The backend refuses to start without a credential-encryption key: 32 random
bytes, base64-encoded, kept in `.env` as `MELD_CREDENTIAL_ENCRYPTION_KEY` (the
export loop above picks it up). Generate one per environment:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Provider base URLs default to the real services; override them with
`OPENROUTER_BASE_URL`, `OPENAI_BASE_URL`, `GROQ_BASE_URL` or `OLLAMA_BASE_URL`
for a gateway or a local stub. Continuing with the session, headers and
workspace from the section above:

```powershell
$cred = Invoke-RestMethod "$base/workspaces/$($ws.id)/providers" -Method Post -WebSession $s -Headers $h -ContentType 'application/json' -Body (@{
  provider = 'OPENROUTER'; displayName = 'My OpenRouter key'; apiKey = $env:MELD_SMOKE_PROVIDER_KEY
} | ConvertTo-Json)
$agent = Invoke-RestMethod "$base/workspaces/$($ws.id)/agents" -Method Post -WebSession $s -Headers $h -ContentType 'application/json' -Body (@{
  name = 'Smoke Agent'; model = 'meta-llama/llama-3.1-8b-instruct:free';
  systemPrompt = 'Answer in one short sentence.'; providerCredentialId = $cred.id;
  temperature = 0.2; maxTokens = 128
} | ConvertTo-Json)
Invoke-RestMethod "$base/workspaces/$($ws.id)/agents/$($agent.id)/test" -Method Post -WebSession $s -Headers $h -ContentType 'application/json' -Body (@{ input = 'Say hello.' } | ConvertTo-Json)
```

Read the provider key from `$env:MELD_SMOKE_PROVIDER_KEY` instead of pasting it
into the script, so it stays out of shell history. The test response contains
output, model, token counts and latency; no endpoint ever returns the key.

### Workflows and executions (M6)

Versions are created whole. A published version is executable; the execution
endpoint only queues durable work, and the background worker claims it.

```powershell
$wf = Invoke-RestMethod "$base/workspaces/$($ws.id)/workflows" -Method Post -WebSession $s -Headers $h -ContentType 'application/json' -Body (@{
  name = 'Smoke Flow'; description = 'agent -> output'
} | ConvertTo-Json)

$version = Invoke-RestMethod "$base/workspaces/$($ws.id)/workflows/$($wf.id)/versions" -Method Post -WebSession $s -Headers $h -ContentType 'application/json' -Body (@{
  publish = $true
  nodes = @(
    @{ key = 'agent'; type = 'agent'; config = @{ agentId = $agent.id; inputTemplate = '{{input.topic}}' } },
    @{ key = 'out'; type = 'output'; config = @{ outputTemplate = @{ answer = '{{agent.output}}' } } }
  )
  edges = @(@{ source = 'agent'; target = 'out' })
} | ConvertTo-Json -Depth 8)

$run = Invoke-RestMethod "$base/workspaces/$($ws.id)/workflows/$($wf.id)/executions" -Method Post -WebSession $s -Headers $h -ContentType 'application/json' -Body (@{
  input = @{ topic = 'Say hello.' }; idempotencyKey = 'smoke-1'
} | ConvertTo-Json -Depth 5)

Start-Sleep -Seconds 2
Invoke-RestMethod "$base/workspaces/$($ws.id)/executions/$($run.id)"   -WebSession $s
Invoke-RestMethod "$base/workspaces/$($ws.id)/executions/$($run.id)/logs" -WebSession $s
```

Repeating the same `idempotencyKey` with the same version and input returns the
original execution. Reusing it with different content returns
`409 IDEMPOTENCY_CONFLICT`.

Execution and tool settings (defaults shown):

| Variable | Default | Purpose |
|---|---|---|
| `MELD_EXECUTION_WORKER_ENABLED` | `true` | Disable the scheduled worker (tests drive it directly) |
| `MELD_EXECUTION_POLL_INTERVAL_MS` | `500` | Queue poll interval |
| `MELD_EXECUTION_HEARTBEAT_INTERVAL_MS` | `10000` | Lease heartbeat interval |
| `MELD_EXECUTION_LEASE_DURATION` | `2m` | How long a claim stays valid |
| `MELD_EXECUTION_MAX_PER_POLL` | `5` | Executions claimed per pass |
| `MELD_EXECUTION_MAX_NODES` | `100` | Safety limit per execution |
| `MELD_TOOLS_ALLOW_INSECURE_HTTP` | `false` | Allow `http://` tool destinations |
| `TOOL_CONNECT_TIMEOUT` / `TOOL_READ_TIMEOUT` | `5s` / `15s` | Tool transport limits |
| `TOOL_MAX_RESPONSE_BYTES` / `TOOL_MAX_REDIRECTS` | `1048576` / `3` | Response and redirect limits |

`http_get` is HTTPS-only by default. Loopback, private, link-local, multicast
and cloud metadata addresses are rejected both when a version is saved (for
literal addresses) and when a connection is attempted; redirects are revalidated
hop by hop.

## 3. Open decisions (needed by phase 4–5, not phase 0–2)

Tracked in `PRD.md` §13. Proposed defaults, not yet ratified:

| Decision | Proposed |
|---|---|
| First provider | **Decided (M5):** OpenRouter through the OpenAI-compatible adapter; default model `meta-llama/llama-3.1-8b-instruct:free` |
| MVP tools | **Decided (M6):** `http_get` (HTTPS-only, default-deny egress) and `json_transform` (local) |
| Workflow I/O | **Decided (M6):** JSON in / JSON out |
| Streaming | No — poll status (`ARCHITECTURE.md` §9) |
| Limits | 120 s/node, 2k output tokens, 20 tool calls, 3 concurrent per workspace |
| Auth transport | **Decided (M4):** HTTP-only session cookie, stateful Spring Security, CSRF double-submit |
| Workspace roles | **Decided (M4):** `OWNER` and `MEMBER`; text column so roles can be added later |

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
- [x] `backend/` scaffold builds and starts (M6)
- [x] Flyway migration applies to a clean `meld` (10 migrations V1–V10)
- [ ] `frontend/` scaffold builds (M7 next)
