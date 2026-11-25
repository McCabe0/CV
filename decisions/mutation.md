## context
Coverage is gated: 90% of the api's lines and branches (jacoco, in `validate-api`), and of the ui's lines,
branches, functions and statements (`jest.shared.mjs`). Coverage says a line ran while the tests did. It
does not say a test would notice if the line did something else. A test with no assertions covers as much
as one with many.

Mutation testing measures that. It makes small changes to the code, one at a time, such as a `>` turned
to `>=`, a value returned as null or a call removed, and runs the tests that cover each one. A change no
test fails on, a surviving mutant, is behaviour nothing checks. The share that fail, the mutation score,
is a measure of the tests, not of the code. Following the digital-resolutions-poc's decision, adapted
(decided 2026-11-24).

## decision
A workflow of its own, `.github/workflows/mutation.yml`, that reports and gates nothing.

- weekly, Monday at 05:00, and by hand. Never on a push: every mutant is another run of the tests that
  cover it, and a slow job in `pipeline.yml` would hold up every push (workflow.md).
- the api with pitest, through its gradle plugin and its junit 5 plugin. The ui with stryker and its jest
  runner, one job per app and package, in parallel with the api.
- informational only. No threshold, a run never fails on its score, it opens no issue, unlike the
  nightly checks, and it commits nothing. Each job puts its score, and the first 50 mutants that survived,
  in its summary (`ci/mutation/summary.mjs`), and keeps its html report for 30 days.

### api: per module, merged
The api is four gradle modules, and each module's tests live beside it. Pitest only runs the tests of the
module it is applied to, so it is applied to `domain`, `application` and `adapters`, each mutating its own
package with its own tests, and `pitestReportAggregate` on the root merges their html into one report. The
aggregator merges only the html, so the summary reads each module's `mutations.xml`, scored as one.

- `app` is left out. It is the wiring, roles and spring boot's main, which the smoke tests check, not its
  own tests, as the poc left out its `config`.
- every junit test runs, testcontainers ones too. Leaving them out would score only the unit tests, lower
  than what the tests really catch.

### spring configuration is left out
The two `@Configuration` classes, `SecurityConfiguration` and `LlmClients`, are not mutated. Their code runs
once, when a test's spring context starts, and spring caches the context: across the tests of a class, and
across the mutants a pitest minion runs one after another in the same jvm. So pitest credits the code to
the one test that happened to start each context, and runs every mutant against a context built before it
was swapped in. In the trial all 54 of `SecurityConfiguration`'s mutants survived, removing its
authorisation rules included, though `ProfileResourceTest` has a test that a request with no token gets a
401. The score would count a blind spot of the tool as a gap in the tests. Their rules are checked by the
resource tests' 401s and 403s, the smoke tests and ZAP.

### kotlin
Kotlin generates code the source does not have: null checks on parameters, and a data class's `equals`,
`hashCode`, `toString`, `copy` and `componentN`. Pitest is told not to mutate calls to kotlin's null checks
or its coroutines' result handling (`avoidCallsTo`), and leaves a data class's generated methods out
(`excludedMethods`), so the score counts code someone wrote.

`avoidCallsTo` replaces pitest's own list rather than adding to it, and that list is the logging
libraries. So they are named again: a removed log line is not a change a test should catch. The poc's
setting misses this.

A property's getter is still mutated. Many of the api's survivors are getters on request and response
dtos, a field returned empty. They stay in the score: a dto's getter is a field of a request or a
response, and one that survives is a field no test reads.

### ui
Stryker's jest runner uses each app's and package's own jest config (`stryker.shared.mjs`):

- it runs from the app's or package's own directory, as an app's jest config only loads its
  `next.config.ts` from there.
- in place (`inPlace`), not in a sandbox, as each jest config imports `../../jest.shared.mjs` from outside
  the directory a sandbox would copy. Stryker puts the files back when it ends. Run by hand, it changes
  the working tree while it runs.
- a test that picks its own environment names stryker's
  (`@stryker-mutator/jest-runner/jest-env/node`), or stryker cannot tell which tests cover a mutant. Each
  app's `proxy.test.ts` does.
- coverage is off for its runs, as each mutant runs only the tests that cover it, which would fail the 90%
  threshold.
- generated types (`*.d.ts`) and test fixtures are left out.

### what each score covers
Each score measures only the tests its tool runs: junit for the api, jest for the ui. The smoke tests,
the playwright tests, ZAP and the load test are not run against mutants, so behaviour only they check
shows as a mutant that survived, or that no test covers. Each summary says so, so neither score is read as
a measure of everything that tests the service.

### what it is not
Not a gate, and not a target to raise for its own sake. A surviving mutant is a question about a test,
not a defect: some are equivalent, a change that alters nothing a caller could see, and are accepted.

## running it locally
- the api: `./gradlew pitest pitestReportAggregate` in `cv-api`, about 10 minutes, with docker running for
  the testcontainers tests. The merged report is `build/reports/pitest/index.html`, and each module's in
  its own `build/reports/pitest/`.
- the ui: `pnpm mutation` in an app or package, or in `cv-ui` for all of them, one at a time, in a linux
  container on a copy, not in the working tree: stryker mutates files in place, and on windows it can fail
  to put them back. For example, from the repository's root:
  `git archive HEAD cv-ui | docker run --rm -i node:24 sh -c 'tar -x && cd cv-ui && corepack enable && pnpm install --frozen-lockfile && cd packages/auth && pnpm mutation'`.
  Reports go to each one's `reports/mutation/`, inside the container.
- the summary script's tests: `node --test ci/mutation/summary.test.mjs`.

## trial
Run locally on 2026-11-24 and 25, before the workflow first ran. The api on the host with docker for
testcontainers, the ui in a `node:24` container on a git copy of `cv-ui`, one app or package at a time.

| | mutants | caught | score | time |
|---|---|---|---|---|
| api `domain` | 285 | 205 | 71.9% | 19s |
| api `application` | 92 | 89 | 96.7% | 15s |
| api `adapters` | 456 | 344 | 75.4% | 7m 45s |
| **api** | **833** | **638** | **76.6%** | |
| ui `packages/auth` | 329 | 277 | 84.2% | 39s |
| ui `packages/api-client` | 12 | 12 | 100% | 11s |
| ui `packages/ui` | 168 | 163 | 97.0% | 3m 37s* |
| ui `apps/shell` | 68 | 65 | 95.6% | 19s |
| ui `apps/profile` | 266 | 242 | 91.0% | 2m 31s |
| ui `apps/cv` | 345 | 292 | 84.6% | 1m 17s |
| ui `apps/jobs` | 162 | 155 | 95.7% | 20s |
| ui `apps/admin` | 270 | 245 | 90.7% | 53s |
| **ui** | **1,620** | **1,451** | **89.6%** | |

* while pitest shared the cpu.

What the trial changed:

- `adapters` first scored 68% of 539. 83 of those mutants were in the two spring configuration classes, and all 54
  of `SecurityConfiguration`'s survived, so both are left out (above). Logging was named again in `avoidCallsTo`.
- stryker run in place on windows could not put `api-client`'s files back, as another process held them
  (`EPERM`), and left two packages mutated in the working tree. Locally, it is run in a linux container
  (below). Ci's checkout is linux and thrown away, so it does not meet this.
- the summary unescapes pitest's xml, so a change reads as the code would.

What survived, worth a look:

- most of the api's survivors, 112 of 195, are a getter returning an empty value: a field of a dto or an
  entity that no test reads.
- `packages/auth`: a sign in's `returnTo` is never checked to come back. Its test checks only the
  redirect to keycloak, so `safeReturnTo` sending everyone to `/` survives. The cookies' `sameSite`,
  `path` and the login cookie's `maxAge` are not asserted, nor clearing them on sign out.
- the ui's length limits: `>` turned to `>=` survives in the profile and admin forms' checks, as no test
  sits at the limit.
- tests for the two above were added on 2026-11-25: sign in's round trip to `returnTo`, the cookies'
  settings and clearing, and every form limit at exactly its length. Each fails with its mutant put back.
- `domain`: `CrawlResult.worked`, `Matching.rank`'s ties and `Review.approved` have conditions no test
  turns over.

Equivalent, accepted: about 15 of the api's are kotlin's own code, its overflow check in `count {}`
(`throwCountOverflow`) and the methods `Skills` gets from delegating to a `List`. Leaving them out would
need method names that hide written code too.

## consequences
- a measure of whether the tests catch a change, which coverage cannot give, for both halves the same way.
- a list of the behaviour no test checks, from the surviving mutants, to work through.
- nothing added to a push's time, and nothing that fails, opens an issue or commits. About an hour of
  runner minutes a week.
- one more report. It is worth keeping only if someone reads it.
