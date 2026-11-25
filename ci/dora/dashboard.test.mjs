// That the DORA dashboard's figures are right, from the events it counts: deployments, sent the way a
// deployment sends them, and failures, sent the way grafana's alerts send them. It runs the dashboard's
// own queries, read from its json, so a panel edited wrong fails here. Needs the stack running, with the
// collector, loki, prometheus and grafana. The last test stops the database for a few minutes, which the
// local environment then counts as a failure of the version running, as it was one. Nightly in ci, on a
// stack of its own. See decisions/dora.md.
//
// Usage: node --test ci/dora/dashboard.test.mjs, from the repository's root.
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { after, describe, it } from 'node:test'
import { setTimeout as sleep } from 'node:timers/promises'

const grafanaUrl = process.env.DORA_GRAFANA_URL ?? 'http://localhost:3300'
const collectorUrl = process.env.DORA_COLLECTOR_URL ?? 'http://localhost:4318/v1/logs'
const apiUrl = process.env.DORA_API_URL ?? 'http://localhost:8080/api'
const keycloakUrl = process.env.DORA_KEYCLOAK_URL ?? 'http://localhost:8180'
const grafana = { Authorization: `Basic ${Buffer.from('admin:admin').toString('base64')}` }

const git = (...args) => execFileSync('git', args, { encoding: 'utf8' }).trim()
const compose = (...args) =>
  execFileSync('docker', ['compose', ...args], { encoding: 'utf8' }).trim()

const dashboard = JSON.parse(readFileSync('local/grafana/dashboards/dora.json', 'utf8'))
// The dashboard's own window, so one longer than loki will answer fails here.
const days = Number(/^now-(\d+)d$/.exec(dashboard.time.from)?.[1])
const queryOf = (title) => {
  const panel = dashboard.panels.find((p) => p.title === title)
  if (!panel) throw new Error(`the DORA dashboard has no panel "${title}"`)
  return panel.targets[0].expr
}

const FREQUENCY = 'Deployment frequency, per day'
const LEAD_TIME = 'Lead time for changes, median'
const FAILURE_RATE = 'Change failure rate'
const RESTORE = 'Time to restore, median'

const json = async (response) => {
  const text = await response.text()
  assert.ok(response.ok, `${response.status}: ${text}`)
  return JSON.parse(text)
}

/** A panel's frames, for an environment, over the dashboard's window, as grafana works them out. */
const query = async (title, environment, range) => {
  const expr = queryOf(title)
    .replaceAll('$environment', environment)
    .replaceAll('$__interval', '1h')
  const { results } = await json(
    await fetch(`${grafanaUrl}/api/ds/query`, {
      method: 'POST',
      headers: { ...grafana, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        queries: [
          {
            refId: 'A',
            datasource: { uid: 'loki', type: 'loki' },
            expr,
            queryType: range ? 'range' : 'instant',
            ...(range && { intervalMs: 3_600_000 }),
          },
        ],
        from: dashboard.time.from,
        to: 'now',
      }),
    }),
  )
  assert.equal(results.A.error, undefined, results.A.error)
  return results.A.frames ?? []
}

/** A stat panel's figure. */
const figure = async (title, environment) =>
  (await query(title, environment, false))[0]?.data.values.at(-1)?.[0]

/** A chart's buckets, as [time, value] pairs. */
const series = async (title, environment) => {
  const [times = [], values = []] = (await query(title, environment, true))[0]?.data.values ?? []
  return times.map((time, i) => [time, values[i]])
}

/** Asks until the answer is the one wanted, as loki takes a moment to make what it is sent searchable. */
const until = async (ask, wanted, { timeout = 60_000, every = 2_000 } = {}) => {
  const end = Date.now() + timeout
  let last
  while (Date.now() < end) {
    last = await ask().catch((error) => error)
    if (wanted(last)) return last
    await sleep(every)
  }
  assert.fail(`still ${JSON.stringify(last)} after ${timeout / 1000}s`)
}

const deploy = (environment, version, running, started, ended) =>
  execFileSync(process.execPath, [
    'ci/dora/deployment-event.mjs',
    ...['--environment', environment, '--version', version],
    ...(running ? ['--running', running] : []),
    ...['--started', started.toISOString(), '--ended', ended.toISOString()],
    ...['--endpoint', collectorUrl],
  ])

/** The median as loki works out a quantile: between the two middle values when there is an even number. */
const median = (values) => {
  const sorted = [...values].sort((a, b) => a - b)
  const rank = (sorted.length - 1) / 2
  const below = Math.floor(rank)
  return sorted[below] + (sorted[Math.ceil(rank)] - sorted[below]) * (rank - below)
}

/** Sends an alert through grafana's dora contact point, as an alert firing would. */
const fireThroughGrafana = async (labels) => {
  const receivers = `${grafanaUrl}/apis/notifications.alerting.grafana.app/v1beta1/namespaces/default/receivers`
  const { items } = await json(await fetch(receivers, { headers: grafana }))
  const dora = items.find((r) => r.spec.title === 'dora')
  const [integration] = dora.spec.integrations
  const result = await json(
    await fetch(`${receivers}/${dora.metadata.name}/test`, {
      method: 'POST',
      headers: { ...grafana, 'Content-Type': 'application/json' },
      body: JSON.stringify({ integration, alert: { labels, annotations: {} } }),
    }),
  )
  assert.equal(result.status, 'success', JSON.stringify(result))
}

describe('the DORA dashboard', () => {
  it('works out the four figures from deployments and an alert', async () => {
    const environment = `test-${randomUUID().slice(0, 8)}`
    // Five commits, oldest first: the first is running, then the others are deployed in turn, ten
    // minutes apart, the last of them ten minutes ago.
    const versions = git('log', '--format=%H', '-n', '5').split('\n').reverse()
    const now = Math.floor(Date.now() / 1000)
    const leadTimes = []
    for (let i = 1; i <= 4; i++) {
      const ended = now - (50 - i * 10) * 60
      deploy(
        environment,
        versions[i],
        versions[i - 1],
        new Date((ended - 60) * 1000),
        new Date(ended * 1000),
      )
      const committed = git('log', '--format=%ct', `${versions[i - 1]}..${versions[i]}`).split('\n')
      leadTimes.push(...committed.map((time) => ended - Number(time)))
    }

    assert.ok(days > 0, "the dashboard's window is in days")
    // The third deployment hurt the environment: its alert fired, through grafana, and resolved ten
    // minutes later. Grafana's test sends only a firing, so the restore is sent as it would send it.
    await fireThroughGrafana({ alertname: 'API errors', environment, service_version: versions[3] })
    const restored = await fetch(collectorUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        resourceLogs: [
          {
            resource: {
              attributes: [
                { key: 'service.name', value: { stringValue: 'skill2career' } },
                { key: 'deployment.environment.name', value: { stringValue: environment } },
              ],
            },
            scopeLogs: [
              {
                logRecords: [
                  {
                    timeUnixNano: `${now}000000000`,
                    attributes: [
                      { key: 'event.name', value: { stringValue: 'restore' } },
                      { key: 'version', value: { stringValue: versions[3] } },
                      { key: 'restore_seconds', value: { intValue: '600' } },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      }),
    })
    assert.ok(restored.ok, `the collector said ${restored.status}`)

    await until(
      () => figure(FAILURE_RATE, environment),
      (rate) => rate === 0.25,
    )
    assert.ok(Math.abs((await figure(FREQUENCY, environment)) - 4 / days) < 1e-6)
    assert.ok(Math.abs((await figure(LEAD_TIME, environment)) - median(leadTimes)) < 1e-3)
    assert.equal(await figure(RESTORE, environment), 600)

    // The chart counts all four, and puts none of them after now, where grafana would not show it.
    const buckets = await series('Deployments, per hour', environment)
    assert.equal(
      buckets.reduce((sum, [, count]) => sum + count, 0),
      4,
    )
    for (const [time] of buckets) assert.ok(time <= Date.now())
  })

  it('gives an environment with no failures a failure rate of none, not no figure', async () => {
    const environment = `test-${randomUUID().slice(0, 8)}`
    const now = new Date()
    deploy(environment, git('rev-parse', 'HEAD'), undefined, now, now)

    await until(
      () => figure(FREQUENCY, environment),
      (frequency) => Math.abs(frequency - 1 / days) < 1e-6,
    )
    assert.equal(await figure(FAILURE_RATE, environment), 0)
  })

  describe('a real failure', () => {
    after(() => compose('start', 'db'))

    // Stopping the database makes the api fail. Its alert waits a minute before firing, the metrics
    // arrive every 15 seconds, and the alert resolves a minute after the failures stop.
    it(
      'fires an alert against the running version, and counts its restore',
      { timeout: 8 * 60_000 },
      async () => {
        const version = execFileSync(
          'docker',
          [
            'inspect',
            compose('ps', '-q', 'api'),
            '-f',
            '{{index .Config.Labels "org.opencontainers.image.revision"}}',
          ],
          { encoding: 'utf8' },
        ).trim()
        assert.match(version, /^[0-9a-f]{40}$/, "the api's image says which commit it is")
        const now = new Date()
        deploy('local', version, undefined, now, now)
        // The test realm's public client that allows the password grant, as the api's own tests use.
        const { access_token: token } = await json(
          await fetch(`${keycloakUrl}/realms/skill2career/protocol/openid-connect/token`, {
            method: 'POST',
            body: new URLSearchParams({
              grant_type: 'password',
              client_id: 'cv-api',
              username: 'candidate',
              password: 'test',
            }),
          }),
        )
        const since = Date.now()

        compose('stop', 'db')
        let failing = true
        const traffic = (async () => {
          while (failing) {
            await Promise.allSettled(
              Array.from({ length: 5 }, () =>
                fetch(`${apiUrl}/profile`, {
                  headers: { Authorization: `Bearer ${token}` },
                  signal: AbortSignal.timeout(40_000),
                }),
              ),
            )
          }
        })()

        const events = async (kind) => {
          const params = new URLSearchParams({
            query: `{service_name="skill2career", deployment_environment_name="local"} | event_name="${kind}"`,
            start: `${since}000000`,
          })
          const response = await fetch(
            `${grafanaUrl}/api/datasources/proxy/uid/loki/loki/api/v1/query_range?${params}`,
            { headers: grafana },
          )
          if (!response.ok) return []
          return (await response.json()).data.result.map(({ stream }) => stream)
        }

        await until(
          async () => (await events('change.failure')).map((e) => e.failed_version),
          (failed) => Array.isArray(failed) && failed.includes(version),
          { timeout: 4 * 60_000, every: 5_000 },
        )

        failing = false
        compose('start', 'db')
        await traffic

        const restores = await until(
          async () => (await events('restore')).filter((e) => e.version === version),
          (found) => Array.isArray(found) && found.length > 0,
          { timeout: 3 * 60_000, every: 5_000 },
        )
        assert.ok(Number(restores[0].restore_seconds) > 0)
        assert.ok((await figure(FAILURE_RATE, 'local')) > 0)
      },
    )
  })
})
