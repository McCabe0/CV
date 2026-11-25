## decision
GitHub Actions, as the digital-resolutions-poc. Built up a slice at a time.

| workflow | when | does |
|---|---|---|
| `pull-request.yml` | every pull request | validate-api, validate-ui and the images, for what the change touches |
| `pipeline.yml` | push to main | images, then smoke (with ZAP's ui scans) and cve, and the deploy test when the charts change |
| `_validate-api.yml` | called | `./gradlew check`: ktlint, unit and integration tests, 90% coverage |
| `_validate-ui.yml` | called | prettier, eslint, typecheck, jest with 90% coverage |
| `_image.yml` | called | builds an image, pushes it to ghcr tagged with the commit |
| `_smoke.yml` | called | playwright against the compose stack, then ZAP's passive scans of the ui (zap.md) |
| `_zap.yml` | called | OWASP ZAP, all three scans, attacks on the api included, on a stack of its own |
| `_cve.yml` | called | grype on every image: fails on high or critical with a fix (cve.md) |
| `nightly.yml` | daily, or by hand | `_cve.yml`, `_zap.yml`, k6 and the deploy test on the newest images, keeping one issue open while any fails |
| `_perf.yml` | called | k6 on the api at a fixed rate: fails on errors, warns on a p99 over target (performance.md) |
| `perf-ceiling.yml` | by hand | `_perf.yml` stepping the rate up, to find the ceiling |
| `_deploy-test.yml` | called | the helm charts linted, policy scanned and installed on kind, then playwright through its gateway (deploy.md) |
| `mutation.yml` | weekly | pitest and stryker, report only (mutation.md) |

## keeping it quick
Each check slice 7 adds would otherwise build the whole stack again, and run on every push (decided
2026-11-24):
- **build once.** The `images` job builds and pushes the six images. Every check that needs them pulls
  them by commit and never builds, smoke, zap and k6 through `.github/actions/stack` and
  `docker-compose.ci.yml`, the deploy test into its kind cluster: about 8 minutes saved per job, and
  each check runs exactly what ships.
- **slow checks nightly.** ZAP's attack scan of the api, the k6 load test and the deploy test, which take
  long and rarely fail, run in `nightly.yml` each morning, or by hand, not on every push. The push keeps
  the quick gates: validate, images, smoke with ZAP's passive ui scans, and cve. The deploy test also
  runs on a push that changes the charts or the test (2026-11-25). The cost: a
  security or performance regression is caught the next morning, not on its own push.
- **only what a pull request touches.** A change to `cv-ui` alone skips the api's checks and image, and
  the reverse. A change to `.github`, `ci` or compose runs everything. Skipped jobs leave required
  checks waiting, so branch protection requires the one `checks` job, which passes when every job that
  ran passed. Pushes to main always run everything, as each commit needs its full set of images.

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
