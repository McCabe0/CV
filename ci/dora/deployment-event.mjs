// Sends a deployment's events to the collector, over otlp, for the DORA dashboard: the deployment, each
// commit in it with its lead time, and, for a rollback, the failure of the version it replaced. The
// deploy job works out everything, so the dashboard only counts. See decisions/dora.md.
//
// Usage: node ci/dora/deployment-event.mjs --environment local --version <sha> [--running <sha>]
//   [--running-since <time>] [--started <time>] [--ended <time>] [--result success|failure]
//   [--endpoint http://localhost:4318/v1/logs | --out <file>] [--repository <path>]
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { parseArgs } from 'node:util'

const git = (repository, ...args) =>
  execFileSync('git', ['-C', repository, ...args], { encoding: 'utf8' }).trim()

const isAncestor = (repository, older, newer) => {
  try {
    git(repository, 'merge-base', '--is-ancestor', older, newer)
    return true
  } catch {
    return false
  }
}

/** Each commit's sha and committer time, in seconds, newest first. */
const commitsOf = (repository, range) =>
  git(repository, 'log', '--format=%H %ct', ...range)
    .split('\n')
    .filter(Boolean)
    .map((line) => {
      const [sha, time] = line.split(' ')
      return { sha, time: Number(time) }
    })

/**
 * What a deployment shipped. The commits since the running version, or only its own when nothing known
 * runs. The running version again ships nothing. A version older than the running one is a rollback,
 * which ships nothing either.
 */
export const changeOf = (repository, version, running) => {
  const known = running && running !== 'unknown'
  if (known && running === version) return { rollback: false, commits: [] }
  if (known && isAncestor(repository, version, running)) return { rollback: true, commits: [] }
  const range = known ? [`${running}..${version}`] : ['-1', version]
  return { rollback: false, commits: commitsOf(repository, range) }
}

const seconds = (time) => Date.parse(time) / 1000
const nanos = (time) => `${BigInt(Date.parse(time)) * 1_000_000n}`

const attribute = (key, value) => ({
  key,
  value:
    typeof value === 'number'
      ? { intValue: `${Math.round(value)}` }
      : typeof value === 'boolean'
        ? { boolValue: value }
        : { stringValue: value },
})

const short = (sha) => sha.slice(0, 7)

/** What a person reads in the dashboard's list of events. */
const describe = (fields) => {
  switch (fields['event.name']) {
    case 'deployment':
      return `${fields.rollback ? 'rolled back to' : 'deployed'} ${short(fields.version)}, ${fields.result}, ${fields.commits} commits`
    case 'deployment.commit':
      return `shipped ${short(fields.commit)} in ${short(fields.version)}, ${Math.round(fields.lead_time_seconds / 60)} minutes after its commit`
    case 'change.failure':
      return `${short(fields.failed_version)} failed: ${fields.reason}`
    default:
      return `${short(fields.version)} restored after ${Math.round(fields.restore_seconds / 60)} minutes`
  }
}

const record = (time, attributes) => ({
  timeUnixNano: nanos(time),
  severityText: attributes['event.name'] === 'change.failure' ? 'ERROR' : 'INFO',
  body: { stringValue: describe(attributes) },
  attributes: Object.entries(attributes).map(([key, value]) => attribute(key, value)),
})

/** The otlp logs request for a deployment. */
export const deploymentEvents = ({
  environment,
  version,
  running,
  runningSince,
  started,
  ended,
  result,
  change,
}) => {
  const records = [
    record(ended, {
      'event.name': 'deployment',
      version,
      previous_version: running ?? 'none',
      result,
      rollback: change.rollback,
      commits: change.commits.length,
      duration_seconds: seconds(ended) - seconds(started),
    }),
  ]
  // Only a deployment that worked brought its commits to the environment.
  if (result === 'success') {
    for (const commit of change.commits) {
      records.push(
        record(ended, {
          'event.name': 'deployment.commit',
          version,
          commit: commit.sha,
          lead_time_seconds: seconds(ended) - commit.time,
        }),
      )
    }
    // A rollback says the version it replaced failed, and that it is restored now.
    if (change.rollback && running) {
      records.push(
        record(ended, {
          'event.name': 'change.failure',
          failed_version: running,
          reason: 'rollback',
        }),
      )
      if (runningSince) {
        records.push(
          record(ended, {
            'event.name': 'restore',
            version: running,
            restore_seconds: seconds(ended) - seconds(runningSince),
          }),
        )
      }
    }
  }
  return {
    resourceLogs: [
      {
        resource: {
          attributes: [
            attribute('service.name', 'skill2career'),
            attribute('deployment.environment.name', environment),
          ],
        },
        scopeLogs: [{ scope: { name: 'deployment' }, logRecords: records }],
      },
    ],
  }
}

if (import.meta.main) {
  const { values } = parseArgs({
    options: {
      environment: { type: 'string' },
      version: { type: 'string' },
      running: { type: 'string' },
      'running-since': { type: 'string' },
      started: { type: 'string' },
      ended: { type: 'string' },
      result: { type: 'string', default: 'success' },
      endpoint: { type: 'string', default: 'http://localhost:4318/v1/logs' },
      repository: { type: 'string', default: '.' },
      out: { type: 'string' },
    },
  })
  if (!values.environment || !values.version) {
    throw new Error(
      'Usage: node ci/dora/deployment-event.mjs --environment <name> --version <sha> ...',
    )
  }
  const now = new Date().toISOString()
  const version = git(values.repository, 'rev-parse', values.version)
  const running =
    values.running && values.running !== 'unknown'
      ? git(values.repository, 'rev-parse', values.running)
      : undefined
  const body = deploymentEvents({
    environment: values.environment,
    version,
    running,
    runningSince: values['running-since'],
    started: values.started ?? now,
    ended: values.ended ?? now,
    result: values.result,
    change: changeOf(values.repository, version, running),
  })
  const records = body.resourceLogs[0].scopeLogs[0].logRecords.length
  // Where there is no collector, as in the pipeline, the events are kept instead.
  if (values.out) {
    writeFileSync(values.out, JSON.stringify(body))
    console.log(
      `${values.environment}: deployed ${version.slice(0, 7)}, ${records} events in ${values.out}`,
    )
    process.exit(0)
  }
  const response = await fetch(values.endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok)
    throw new Error(`the collector said ${response.status}: ${await response.text()}`)
  console.log(`${values.environment}: deployed ${version.slice(0, 7)}, ${records} events sent`)
}
