## context
The smoke tests and ZAP say nothing about how the api behaves with many people at once. The query most
likely to slow down is the job match: a full text search over every live job, for each candidate's
skills. Following the digital-resolutions-poc's decision, adapted (decided 2026-11-24).

## decision
A load test with k6, nightly, or by hand: not on every push, to keep the pipeline quick (workflow.md).

- k6: the script is TypeScript (`ci/perf/load.ts`), it runs from docker, it is free, and its
  thresholds fail a run. `ci/perf.sh` runs it, `_perf.yml` in ci.
- api only, on a stack of its own (`docker-compose.perf.yml`): the api and what it needs, from the
  commit's pushed images. No cv-worker or job-worker: no CV is written and no board crawled during a run.
  A browser would mostly measure itself.
- a fixed rate of 50 requests a second, not a number of users. Users wait for a reply, so a slow api
  slows its own load and hides the problem. Nobody has an expected peak yet, so it is a starting target,
  to rise once the headroom is known: `perf-ceiling.yml`, by hand, steps the rate up until it breaks.
- the target is a p99 of 200ms, overall, with each endpoint reported.
- a run fails on more than 1% failed requests, or 100 it could not start: the api breaking under load.
  A p99 over the target is a warning, not a failure: the job warns and the summary says so, as the test
  and the stack share a runner, and a busy one moves the p99 with no change to the api.
- data at production's size: 2,000 live jobs across the tracked companies, inserted straight into
  postgres (`ci/perf/jobs.sql`; a real crawl found 1,431), and 20 candidates made through keycloak's admin
  api, each with a profile of different skills, so each matches a different set. Each gets a CV request
  and a fit summary in setup, to be read during the run.
- the mix, a request at a time: job matches 54% (plain, remote only, and near London), the profile 20%,
  a CV request 10%, a fit summary 10%, the admin's companies 5%, and saving the profile 1%, the only
  write, saving the same one so the data stays the same.
- keycloak only before the run: the api checks tokens against cached keys. Tokens get an hour, as ZAP's.
- the api and the database get 2 cores each, so neither has more of the machine, and a run compares with
  the last.
- 1ms of latency between the api and the database, added by toxiproxy once set up, about a database in
  the same region. They share a machine here and would not in production, which flatters an api that
  waits on many queries.
- reports: k6's summary and html report, and each container's cpu and memory every 5 seconds
  (`stats.csv`), which shows if the machine was the limit rather than the api.

## running it locally
On the e2e stack (see decisions/ui.md): `COMPOSE_PROJECT_NAME=cv-e2e ci/perf.sh`. It recreates the api on
2 cores behind toxiproxy, and puts it back after. `PERF_RATE=100` for another rate, `PERF_PROFILE=step`
for the ceiling. The jobs and candidates it adds are kept for the next run. Reports go to
`ci/perf-report`.

## costs
- about 10 minutes of runner time a night.
- a regression is caught the next morning, not on its own push.
- it does not measure capacity. The test and the stack share a runner, so the figures only compare with
  each other. Real capacity needs a deployed environment.
