## context
The four DORA metrics say how often a team ships, how long a change takes to reach production, how often
a change hurts production, and how long production takes to recover. They are about delivery, not code.

All four are about production. A test that fails in the pipeline is not a change failure: it is the
pipeline doing its job, and it shows in lead time, as the change takes longer to ship. Nothing here
deploys yet (deploy.md), so something has to stand in for production, and it has to be easy to replace
when production exists.

The local stack already has what production telemetry would give: traces, metrics and logs through the
collector, into jaeger, prometheus and loki, read in grafana (observability.md).

Following the digital-resolutions-poc's decision, adapted (decided 2026-11-25).

## decision
A live DORA dashboard in grafana, built from two kinds of event: deployments, and failures in what was
deployed. Nothing in it is specific to github or to a cloud. It is shown on the local stack for now, as
that is where there is telemetry to see.

### deployments
A deployment sends one event, through the collector, as a log event that loki keeps
(`ci/dora/deployment-event.mjs`):

- the environment, the version (the commit's sha), when it started and ended, and whether it succeeded.
- each commit in the change, the ones since the version it replaced, with its own lead time: from the
  commit to the end of the deployment. The deployment knows the range, so the dashboard does no git.
  - the range is `git log <running>..<deploying>`. What is running comes from the environment itself, not
    from a record of what was sent, so a failed or a manual deployment cannot skew it: the running api's
    `org.opencontainers.image.revision` label locally, set at build beside `service.version`, and a helm
    release's image in production. A first deployment counts only its own commit.
  - a commit whose pipeline fails is not deployed, so the next one ships it with its fix, and its lead
    time runs from it, the first commit that tried, to the deployment that passed.
  - a commit's time is when it was committed, not written: its committer time, which a rebase moves to
    when it reached the branch.
- whether it is a rollback, a version older than the one it replaced. A rollback also says the version
  it replaced failed, and is restored.

| metric | from |
|---|---|
| deployment frequency | successful deployments, per day |
| lead time for changes | the commits' lead times, the median |

Until something deploys, two things stand in for a deployment:

- on the local stack, `local/deploy.sh`, which builds the commit's images, starts them, and sends the
  event. Its environment is `local`.
- in ci, a commit on main that passed every check: the pipeline's `record` job. It cannot reach a
  collector, so it records the event as a github deployment to the environment `main`, with the events
  as its payload, for the history, and for loading into loki later. The commit it replaces is the last
  one recorded there.

When a deploy job exists, it sends the event, and `record` stops. The dashboard does not change.

### failures
A change failure is a deployment that harms the environment enough that someone has to act: an alert, a
rollback, or a fix deployed for it.

- every span, metric and log carries `service.version`, the commit's sha, set at build. So a failure
  belongs to the version that was running when it began, and grafana can split error rates and latency by
  version.
- two alerts, in grafana alerting, on the api's own request metrics, by version
  (`local/grafana/provisioning/alerting/dora.yaml`). Each is one someone would have had to act on, as a
  noisy alert would count as a failure that was not:
  - more than 5% of the api's requests a 5xx, for a minute.
  - the api's p99 over 200ms, the bar the load test holds it to (performance.md), for five minutes.
  - not its health check, which is not a metric. An api that is down sends nothing, so an alert on that
    would have no version to blame. The 5xx alert is what a broken database or dependency shows as.
- a deployment that a rollback replaces has failed, alert or not.

| metric | from |
|---|---|
| change failure rate | versions with a failure, as a share of successful deployments, over the window |
| time to restore | from the alert firing to it resolving, the median. For a rollback, from when the version it replaced started to the rollback's end |

DORA now calls the last one failed deployment recovery time.

Alerts reach loki as events through the collector: a grafana webhook contact point, with a custom
payload, sends each alert's firing and resolving as an otlp log event, straight to the collector, so it
needs no other receiver. The resolving event carries how long it took, from grafana's own times. Grafana
sends each alert alone and at once.

### the dashboard
One grafana dashboard, provisioned beside the stack's (`local/grafana/dashboards/dora.json`,
http://localhost:3300/d/dora): the four figures over a window, deployments and lead time by hour, and
every deployment, failure and restore as a list. It has an environment to choose, `local` by default.

The window is the last 7 days by default, and any range can be chosen. Loki answers no query longer than
30 days and an hour by default (`max_query_length`), so a longer one needs that raised.

### the test
`ci/dora/dashboard.test.mjs`, nightly in `nightly.yml` on a stack of its own, and locally on a running
stack with `node --test ci/dora/dashboard.test.mjs`. It runs the dashboard's own queries, read from its
json, through grafana's api, so a panel that is edited wrong fails it, and nothing in the test copies a
query that could drift. Each run has an environment of its own, `test-` and a random suffix, so it never
reads another run's events or the local ones.

1. seeded, with exact figures. Four deployments of the repository's last five commits, sent with
   `deployment-event.mjs`, so the script and its git are tested too. One alert firing for one of their
   versions, sent through grafana's own contact point, with its test api, so its template is tested,
   and the alert resolving, sent as grafana sends it. It checks four deployments over the window, a
   failure rate of 25%, the median lead time it works out from git, and the restore time it set. And
   that an environment with no failures shows 0%, not nothing.
2. one real failure. It stops the database, which makes the api's requests fail, waits for the 5xx alert
   to fire against the running version, then starts the database and waits for the alert to resolve with
   a restore time. A few minutes, as the alert waits a minute before firing. It counts as a failure of
   the running version in `local`, as it was one, so run it on a stack whose figures do not matter.

`deployment-event.mjs` has unit tests of its own, on a repository they make:
`node --test ci/dora/deployment-event.test.mjs`.

### in production
On the target in deploy.md, helm on EKS, deployed from github actions:

1. the deploy job, in a github environment `production` with whatever approval it needs, takes an AWS
   role through OIDC, with no stored keys, and with it EKS access for helm.
2. it reads the running version from the release's image, which gives the range of commits.
3. `helm upgrade --atomic --wait`, with the images the pipeline passed, by digest. Their
   `service.version` is already the commit's.
4. it sends the deployment event, success or not, to the collector in the cluster, through a port
   forward for that one call: the access helm already has, so no public otlp endpoint and no other
   credential.
5. one event per release, as the umbrella chart ships all the images together.

- the collector's exporters point at the platform's backends. The apps and the events do not change.
- every alert has two contact points: on call, as the platform has it, and the webhook for DORA. DORA
  rides beside paging and never replaces it.
- a rollback is a workflow, "roll back to" a sha, so it sends its event like any deployment.
- loki keeps the events for at least the dashboard's window, and better 13 months, for the year's trend.
- events carry shas and times, never commit messages or names. That keeps people's data out of the log
  store, and DORA's figures are for a team, not for ranking the people in it.

## not in scope
- bugs that users report. No telemetry sees a wrong answer sent with a 200. The usual hook is an issue
  labelled as an incident with the version, counted with the alerts. It is a process, not code, and the
  dashboard says it is not counted.
- failures nobody notices, which no alert can count.
- an LLM's poor CV, which is a 200 too, and the critic's job, not an alert's.
- bands, such as elite and low, as the figures stand in for production until it exists.
- `main`'s recorded deployments on the dashboard. They are kept in github until there is a loki to load
  them into.

## open questions
- the alerts' thresholds and how long they must last. They are the first SLOs, so they want agreeing.
- the platform's log store. The dashboard's queries are loki's, so production needs loki, run by us or
  bought.

## trial
Run locally on 2026-11-25, on the compose stack, before the workflows first ran.

- `local/deploy.sh api grafana` built the commit's images, started them, and recorded the deployment,
  with its one commit's lead time, 15 minutes.
- the api's request metrics in prometheus are `http_server_requests_milliseconds_*`, with
  `service_name="cv-api"`, `service_version` and `status`, as the alerts expect.
- `node --test ci/dora/dashboard.test.mjs` passed all three tests in 3 minutes. The seeded figures came
  out exact, through the dashboard's own queries. With the database stopped, `API errors` fired against
  the running version about 90 seconds later, went through grafana's webhook and the collector into loki,
  and resolved a minute after the database was back, as a restore of a minute.
- the test's first deployment of the running version counts as a second deployment of it in `local`,
  as the poc's does. On a stack whose figures matter, run it elsewhere.
- `node --test ci/dora/deployment-event.test.mjs`: 14 tests pass, the one for `--out` written first.

## costs
- about 10 minutes of runner time a night, for the stack and the real failure.
- the collector's otlp port open on the host, for local deployments.
- alert rules that have to be kept honest: one that fires when nobody needs to act makes the failure
  rate wrong.
- all four stand in for production until something deploys, and the dashboard says so.
