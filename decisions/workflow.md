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
| `_cve.yml` | called, and daily | grype on every image |
| `_perf.yml` | called | k6, p99 targets per endpoint |
| `_deploy-test.yml` | called | the helm charts installed on kind |
| `mutation.yml` | weekly | pitest and stryker, report only |

Dependabot keeps gradle, npm, docker and actions up to date.

## delivery
In vertical slices, each one test first, green, and reviewed before the next:
1. skeleton: gradle modules, compose, decisions, core ci
2. profile: api, profile zone and shell sign in
3. CV agents: interviewer, writer and critic, both LLM adapters, outbox and kafka, server sent events
4. job ingest: the board adapters, the scheduler, freshness
5. matching, and the jobs zone
6. the admin zone
7. the rest of the pipeline: zap, grype, k6, mutation, helm on kind, DORA
