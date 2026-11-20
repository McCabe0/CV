# Accounts + History View — Implementation Plan

> Decisions locked in: **auth = email + password (JWT)**, **production DB = managed Postgres** (Neon/Supabase/RDS).
> Companion to [`ROADMAP.md`](../ROADMAP.md). Tick milestones off as they ship.

## Status

- [x] **Milestone 1 — DB foundation** (Postgres config + Flyway baseline + local docker-compose)
- [ ] **Milestone 2 — Auth backend** (accounts, JWT, security config, `/auth/*`)
- [ ] **Milestone 3 — Auth frontend** (login/register, token wiring, app gating)
- [ ] **Milestone 4 — Ownership** (stamp + enforce `owner` on profiles/CVs/matches)
- [ ] **Milestone 5 — History view** (endpoints + UI)

---

## Current state (why this is needed)

`application.properties` previously set only the Gemini key. With H2 on the classpath and no
datasource URL, Spring auto-configured an **in-memory H2** that reset on every restart — so nothing
persisted between sessions. The PostgreSQL driver was already a dependency, so the move to a real DB
is configuration + migrations, not new code.

---

## Part 1 — Connecting to the database (managed Postgres)

### Provision
Pick a managed Postgres (free tiers exist): **Neon** (recommended), **Supabase**, or **AWS RDS**.
You receive a connection string; convert it to JDBC form:

```
jdbc:postgresql://<host>:5432/skill2career?sslmode=require
```

### Spring profiles
- **Dev / tests** → H2 (default profile), Flyway disabled, `ddl-auto=update`/`create-drop`.
- **Prod** (`SPRING_PROFILES_ACTIVE=prod`) → Postgres, Flyway is the schema authority,
  `ddl-auto=validate`.

### Production env vars
```
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<host>:5432/skill2career?sslmode=require
DATABASE_USER=...
DATABASE_PASSWORD=...
GEMINI_API_KEY=...
JWT_SECRET=...            # added in Milestone 2
```

### Local dev against Postgres (optional)
`docker-compose.yml` (repo root) runs `postgres:16`. Start it and run the backend with the `prod`
profile pointed at `localhost:5432`:

```bash
docker compose up -d
# then, for the backend:
SPRING_PROFILES_ACTIVE=prod \
DATABASE_URL=jdbc:postgresql://localhost:5432/skill2career \
DATABASE_USER=skill2career DATABASE_PASSWORD=localdev \
./gradlew bootRun
```

### Migrations (Flyway)
`backend/src/main/resources/db/migration/V1__init.sql` is the baseline matching the current
entities. New schema changes go in `V2__*.sql`, `V3__*.sql`, etc. — never edit an applied migration.

> ⚠️ First-deploy check: the V1 baseline + `ddl-auto=validate` combo should be confirmed against the
> real Postgres on first boot. If Hibernate `validate` flags a column type, adjust V1 (it's a
> pre-deploy baseline, safe to edit until the first real migration runs).

---

## Part 2 — Accounts + history (email + password, JWT)

### Data model
- **New** `UserAccountEntity`: `id`, `email` (unique), `passwordHash` (BCrypt), `displayName`, `createdAt`.
- **Ownership**: nullable `owner_id` FK on `user_profiles`. CVs and matches link to a profile, so
  ownership derives through it. Nullable keeps existing/anonymous rows valid.

### Backend
1. Deps: `spring-boot-starter-security` (BCrypt) + `io.jsonwebtoken:jjwt-*` (HS256).
2. `UserAccountRepository.findByEmail`.
3. `JwtService` — sign/verify HS256 with `JWT_SECRET`; subject = userId; ~24h expiry.
4. `AuthService` — `register` (reject dup email, BCrypt hash), `login` (verify + issue token).
5. `SecurityConfig` — stateless, bearer-token filter, CORS for the SPA origin; permit `/auth/**`,
   `/health`; secure the rest.
6. `AuthController` — `POST /auth/register`, `POST /auth/login`, `GET /auth/me`.
7. Scope existing endpoints to the authenticated user; reads verify ownership (403/404 otherwise).
8. History endpoints — `GET /me/cvs`, `GET /me/matches`, `GET /me/profiles`.

### Frontend
1. `api.ts` — attach `Authorization: Bearer <token>`; on `401`, clear token + redirect to login.
2. `useAuth` context — `{ user, token }`, persist token to `localStorage`, `login/register/logout`.
3. Login + Register screens (reuse the design system).
4. App gating — show auth screen when unauthenticated; signed-in email + "Log out" in the hero.
5. History view — list past CVs and match runs with dates, reopenable into Step 2/3.

### Testing & risks
- Tests: `AuthService` (hash/verify, dup email), security integration (401 w/o token, 200 with),
  ownership isolation (user A cannot read user B's CV).
- CORS: SPA on a different origin must be allowed to send the `Authorization` header (update `WebConfig`).
- Token storage: `localStorage` is simplest but XSS-exposed; httpOnly cookies are safer (adds CSRF).
  Start with `localStorage` + short expiry; revisit later.
- Existing rows: nullable `owner_id` keeps them valid; they just won't appear in anyone's history.

### Build order
DB foundation → Auth backend → Auth frontend → Ownership → History view.
