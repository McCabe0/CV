## context
Skill2Career takes a user's details, writes them a professional CV, and finds live jobs, on the
company's own website, that match their skills. The first version was a flat controller, service and
entity app, with the jobs made up by an LLM. It is rebuilt here, alongside it, in `cv-api` and `cv-ui`,
following the digital-resolutions-poc for its production tooling and test style. The old `backend` and
`frontend` are deleted once the rebuild replaces them.

## key rules
- TDD, always. The failing test is written first, for an endpoint its resource test first, then the
  use case, then the adapter.
- unit tests use hand written fakes of the ports, not mocks. Mocks only at the web slice.
- integration tests use real containers: postgres, keycloak and kafka, through testcontainers. Outside
  http services (the LLMs, the job boards) are wiremock.
- `./gradlew check` holds the rules: ktlint, and 90% line and branch coverage. It must stay green.
- small classes, one reason to change each. A use case is one class with one public function.
- no inline imports, and no wildcard imports.
- default to LAZY for any joins.

## decision: hexagonal, in gradle modules
```
cv-api/
  domain/       pure kotlin. The model and its rules. Depends on nothing.
  application/  use cases (driving ports) and the ports they need (driven ports). Depends on domain only.
  adapters/     spring. web, persistence (jpa), messaging (kafka), llm (gemini, claude),
                job sources (greenhouse, lever, ashby, workable). Depends on application.
  app/          the spring boot main, wiring, security, and the integration tests.
```
The gradle modules enforce the direction: `domain` cannot see spring, and `application` cannot see an
adapter, as neither has them on its classpath. No architecture test is needed for what the compiler
already refuses.

Ports are in the service's own words, never a vendor's: `LlmClient`, not `GeminiClient`; `JobSource`,
not `GreenhouseApi`. Each adapter is chosen by a setting with `@ConditionalOnProperty`, so a missing
or misspelt setting fails at start, not at first use.

## decision: one image, three roles
The same image runs as one or more roles, set by `APP_ROLES`:

| role | does | scales on |
|---|---|---|
| `api` | the http api and its server sent events | requests |
| `cv-worker` | the CV agents: interviewer, writer and critic | the `cv.requested` topic's lag |
| `job-worker` | crawls the job boards and keeps jobs fresh | the `jobs.crawl` topic's lag |

Locally all three can run in one process (`APP_ROLES=api,cv-worker,job-worker`). In production each is
its own deployment with its own autoscaler, so a slow LLM adds cv-workers without adding api pods.
Every role serves `/api/health`, so each can be probed. Only `api` serves the rest of the api.

## decision: postgres and flyway
The database stays postgres, with flyway the only owner of the schema (`ddl-auto: none`). H2 goes:
tests run against postgres in a container, so what is tested is what runs.

## see also
- `agents.md`: the CV agents
- `jobs.md`: where jobs come from
- `messaging.md`: kafka, the outbox, and server sent events
- `ui.md`: the multi zone ui
- `observability.md`: traces, metrics and logs
- `workflow.md`: the pipeline
