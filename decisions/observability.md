## decision
As the digital-resolutions-poc: OpenTelemetry, over otlp, through a collector, to open source backends.
The local stack is what gets promoted, so it runs all of it.

```
ui servers ─ traces ──────────┐          ┌─→ jaeger ─────┐
api, workers ─ traces,        ├→ collector ─→ prometheus ─┼→ grafana
  metrics, logs ──────────────┘          └─→ loki ───────┘
```

- spring boot's own opentelemetry starter, no java agent. The apps only know the collector's address.
- kafka carries the trace context in its headers, so one trace runs from the http request, through the
  outbox and kafka, into the cv-worker and every LLM call it makes.
- each LLM call is a span, with the provider, model, tokens used and round, but never the prompt or the
  answer, which hold personal data.
- metrics beyond http: kafka consumer lag per topic (what the workers scale on), outbox rows waiting,
  critic rounds per CV, LLM latency and errors per provider, jobs crawled and failed per source.
- health checks are not traced.
- production samples 1 request in 10. Compose samples all of them.
