## decision
GitHub Actions, as the digital-resolutions-poc. Built up a slice at a time.

| workflow | when | does |
|---|---|---|
| `pull-request.yml` | every pull request | validate-api, validate-ui |
| `pipeline.yml` | push to main | images, then smoke, cve, perf and observability, then deploy test |
| `_validate-api.yml` | called | `./gradlew check`: ktlint, unit and integration tests, 90% coverage |
| `_validate-ui.yml` | called | prettier, eslint, typecheck, jest with 90% coverage |
| `_image.yml` | called | builds an image, pushes it to ghcr tagged with the commit |
| `_smoke.yml` | called | playwright against the compose stack, and OWASP ZAP |
| `_cve.yml` | called | grype on every image: fails on high or critical with a fix (cve.md) |
| `cve-daily.yml` | daily | `_cve.yml` on the newest images, keeping one issue open while it fails |
| `_perf.yml` | called | k6, p99 targets per endpoint |
| `_deploy-test.yml` | called | the helm charts installed on kind |
| `mutation.yml` | weekly | pitest and stryker, report only |

Dependabot keeps gradle, npm, docker and actions up to date.

The workflows can only run on github, so `actionlint` checks them locally before they are pushed:
`docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.7`. It found, 2026-11-24, that
`pull-request.yml` had not been valid yaml since slice 3, a comment line without its `#`, so no pull
request had been checked since.

## delivery
In vertical slices, each one test first, green, and reviewed before the next:
1. skeleton: gradle modules, compose, decisions, core ci
2. profile: api, profile zone and shell sign in
3. CV agents: interviewer, writer and critic, both LLM adapters, outbox and kafka, server sent events.
   A general CV, aimed at the profile's target role
4. job ingest: the board adapters, the scheduler, freshness
5. matching, and the jobs zone. A CV tailored to one job: the same loop, its brief carrying the job's
   description, so the writer aims at it and the critic checks its keywords
6. the admin zone
7. the rest of the pipeline: zap, grype, k6, mutation, helm on kind, DORA
