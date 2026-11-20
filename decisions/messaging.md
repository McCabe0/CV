## context
Writing a CV takes several LLM calls and can take a minute. Crawling a job board takes seconds to
minutes. Neither can happen inside an http request without tying up the api, and a burst of users would
overload the LLM providers. The work needs a queue.

## decision: kafka, in kraft mode
Apache Kafka, with no zookeeper. Topics:

| topic | key | from | to |
|---|---|---|---|
| `cv.requested` | cv request id | api | cv-worker |
| `cv.progress` | cv request id | cv-worker | api (to server sent events) |
| `jobs.crawl` | company id | job-worker's scheduler | job-worker |

Each has a `.retry` and a `.dlq` topic. A failed message is retried with back off, then parked in the
dead letter topic, where it can be seen and replayed. The key keeps one request's events in order.

## decision: a transactional outbox
A use case saves its state and its event in one database transaction: the event goes to an `outbox`
table. A relay publishes the outbox to kafka and marks each row sent. So a crash between the database
and kafka never loses an event, nor sends one for a change that was rolled back.

Delivery is at least once, so every consumer is idempotent: it records each event id it has handled in
`processed_events`, in the same transaction as its work, and skips an id it has seen.

## decision: 202 and server sent events
- `POST /api/cv-requests` saves the request, returns `202 Accepted` with its id and a `Location`.
- `GET /api/cv-requests/{id}/events` is a server sent event stream of its progress: the interviewer's
  questions, each writer and critic round, and the finished CV.
- `GET /api/cv-requests/{id}` returns its state, for a client that cannot stream, or that reconnects.

The api has many instances, and the one holding a user's stream is not the one that got the progress
event. So every api instance reads `cv.progress` with a consumer group of its own, and passes each event
to the streams it holds. No shared cache is needed.
