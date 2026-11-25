# Skill2Career

Takes a candidate's skills and experience, writes them a CV, and finds live jobs on companies' own job
boards that ask for their skills.

- **Profile:** the candidate's skills, experience, education and work history.
- **CV:** an interviewer asks about gaps in the profile, then a writer and a critic go round by round
  until the CV is good enough. A CV can be general, or aimed at one listed job.
- **Jobs:** the companies' own Greenhouse, Lever, Ashby and Workable boards are crawled daily. A
  candidate sees the jobs that ask for their skills, best first, filtered by remote only and location,
  and can ask why one fits them.

## Structure

```text
cv-api/      Kotlin + Spring Boot, hexagonal: domain, application, adapters, app
             one image, run as the api, the cv-worker or the job-worker
cv-ui/       Next.js micro-frontends in a pnpm workspace: the shell and the profile, cv and jobs zones
decisions/   why it is built the way it is: architecture, agents, jobs, messaging, ui, observability
local/       scripts for the local stack
```

Postgres, Kafka (with an outbox) and Keycloak sit underneath. See `decisions/architecture.md`.

## Run it

Everything runs in Docker Compose. Copy `.env.example` to `.env` and add a Gemini key: it is free from
[Google AI Studio](https://aistudio.google.com/app/apikey). Never commit a real key. The cv-worker does
not start without one.

```bash
docker compose up -d --build --wait
bash local/seed-profiles.sh   # a profile for each test user
bash local/crawl-now.sh       # crawl the job boards now, rather than wait for the daily crawl
```

Then open http://localhost:3000 and sign in as `candidate`, `candidate-other` or `admin`, each with
the password `test`.

| what                         | where                                     |
|------------------------------|-------------------------------------------|
| the app                      | http://localhost:3000                     |
| api, swagger                 | http://localhost:8080/api/swagger-ui.html |
| keycloak admin               | http://localhost:8180 (admin / admin)     |
| kafka ui: topics, lag, dlq   | http://localhost:8090                     |
| grafana: metrics, logs       | http://localhost:3300                     |
| DORA dashboard               | http://localhost:3300/d/dora              |
| jaeger: traces               | http://localhost:16686                    |

The database keeps its data in the `pgdata` volume across `docker compose down`. To start it empty,
use `docker compose down --volumes`.

## DORA

The four DORA metrics, live, on grafana's DORA dashboard. Nothing deploys yet, so the local stack stands
in for production, as the environment `local`, and `local/deploy.sh` deploys to it: it builds the
commit's images, starts them, and records the deployment with each commit's lead time. Grafana's alerts on
the api's errors and latency count as change failures. In ci, a commit on main that passes every check is
recorded as a github deployment to `main`. See `decisions/dora.md`.

```bash
bash local/deploy.sh          # in place of docker compose up, to count as a deployment
```

## Test it

```bash
cd cv-api && ./gradlew check   # ktlint, unit and testcontainers tests, 90% coverage
cd cv-ui && pnpm check         # prettier, eslint, typecheck, jest
```

The Playwright journeys run against the whole stack, with WireMock standing in for Gemini and the job
boards. Run them on their own compose project, so they don't touch the dev stack's data:

```bash
docker compose -p cv-e2e -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --wait shell cv-worker job-worker
cd cv-ui && pnpm e2e
```
