import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { after, before, describe, it } from 'node:test'
import { changeOf, deploymentEvents } from './deployment-event.mjs'

// A repository of its own, with known commit times, as ci checks out only one commit of this one.
let repository
const shas = {}
const commit = (name, file, time) => {
  mkdirSync(join(repository, file, '..'), { recursive: true })
  writeFileSync(join(repository, file), name)
  const env = {
    ...process.env,
    GIT_COMMITTER_DATE: `@${time} +0000`,
    GIT_AUTHOR_DATE: `@${time} +0000`,
  }
  execFileSync('git', ['-C', repository, 'add', '.'], { env })
  execFileSync('git', ['-C', repository, 'commit', '-qm', name], { env })
  shas[name] = execFileSync('git', ['-C', repository, 'rev-parse', 'HEAD'], {
    encoding: 'utf8',
  }).trim()
}

before(() => {
  repository = mkdtempSync(join(tmpdir(), 'deployment-event-'))
  execFileSync('git', ['-C', repository, 'init', '-q'])
  execFileSync('git', ['-C', repository, 'config', 'user.email', 'test@example.com'])
  execFileSync('git', ['-C', repository, 'config', 'user.name', 'test'])
  commit('first', 'a.txt', 1000)
  commit('second', 'b.txt', 2000)
  commit('third', 'c.txt', 3000)
})

after(() => rmSync(repository, { recursive: true, force: true }))

const records = (body) => body.resourceLogs[0].scopeLogs[0].logRecords
const fields = (record) =>
  Object.fromEntries(
    record.attributes.map(({ key, value }) => [
      key,
      value.stringValue ?? value.boolValue ?? Number(value.intValue),
    ]),
  )
const ofKind = (body, kind) =>
  records(body)
    .map(fields)
    .filter((r) => r['event.name'] === kind)

describe('changeOf', () => {
  it('ships the commits since the running version, newest first', () => {
    const change = changeOf(repository, shas.third, shas.first)

    assert.equal(change.rollback, false)
    assert.deepEqual(
      change.commits.map(({ sha }) => sha),
      [shas.third, shas.second],
    )
  })

  it('takes a commit time from when it was committed', () => {
    const [{ time }] = changeOf(repository, shas.second, shas.first).commits

    assert.equal(time, 2000)
  })

  it('ships only its own commit when nothing known runs', () => {
    for (const running of [undefined, 'unknown']) {
      assert.deepEqual(
        changeOf(repository, shas.second, running).commits.map(({ sha }) => sha),
        [shas.second],
      )
    }
  })

  it('ships nothing when the running version is deployed again', () => {
    assert.deepEqual(changeOf(repository, shas.second, shas.second), {
      rollback: false,
      commits: [],
    })
  })

  it('is a rollback when the version is older than the running one', () => {
    assert.deepEqual(changeOf(repository, shas.first, shas.third), { rollback: true, commits: [] })
  })
})

describe('deploymentEvents', () => {
  const deployment = {
    environment: 'local',
    version: 'v2',
    running: 'v1',
    started: '2026-11-07T12:00:00Z',
    ended: '2026-11-07T12:05:00Z',
    result: 'success',
    change: {
      rollback: false,
      commits: [
        { sha: 'c2', time: Date.parse('2026-11-07T11:00:00Z') / 1000 },
        { sha: 'c1', time: Date.parse('2026-11-07T10:00:00Z') / 1000 },
      ],
    },
  }

  it('names the service and the environment, which loki indexes', () => {
    const { attributes } = deploymentEvents(deployment).resourceLogs[0].resource

    assert.deepEqual(fields({ attributes }), {
      'service.name': 'skill2career',
      'deployment.environment.name': 'local',
    })
  })

  it('records the deployment, at its end', () => {
    const body = deploymentEvents(deployment)

    assert.deepEqual(ofKind(body, 'deployment'), [
      {
        'event.name': 'deployment',
        version: 'v2',
        previous_version: 'v1',
        result: 'success',
        rollback: false,
        commits: 2,
        duration_seconds: 300,
      },
    ])
    assert.equal(records(body)[0].timeUnixNano, `${Date.parse('2026-11-07T12:05:00Z')}000000`)
  })

  it('says what happened, for a person reading the events, and marks a failure as an error', () => {
    const body = deploymentEvents({
      ...deployment,
      version: 'abcdef1234',
      running: 'fedcba9876',
      runningSince: '2026-11-07T11:45:00Z',
      change: { rollback: true, commits: [] },
    })

    assert.deepEqual(
      records(body).map(({ severityText, body }) => [severityText, body.stringValue]),
      [
        ['INFO', 'rolled back to abcdef1, success, 0 commits'],
        ['ERROR', 'fedcba9 failed: rollback'],
        ['INFO', 'fedcba9 restored after 20 minutes'],
      ],
    )
    assert.equal(
      records(deploymentEvents(deployment))[1].body.stringValue,
      'shipped c2 in v2, 65 minutes after its commit',
    )
  })

  it('gives each commit its lead time, from its commit to the end of the deployment', () => {
    const commits = ofKind(deploymentEvents(deployment), 'deployment.commit')

    assert.deepEqual(
      commits.map(({ commit, lead_time_seconds }) => [commit, lead_time_seconds]),
      [
        ['c2', 3900],
        ['c1', 7500],
      ],
    )
  })

  it('brings no commits when it failed, as they never reached the environment', () => {
    const body = deploymentEvents({ ...deployment, result: 'failure' })

    assert.equal(ofKind(body, 'deployment')[0].result, 'failure')
    assert.deepEqual(ofKind(body, 'deployment.commit'), [])
  })

  it('is a first deployment when nothing ran before', () => {
    const body = deploymentEvents({ ...deployment, running: undefined })

    assert.equal(ofKind(body, 'deployment')[0].previous_version, 'none')
  })

  it('fails the version a rollback replaces, and restores it from when that version started', () => {
    const body = deploymentEvents({
      ...deployment,
      version: 'v1',
      running: 'v2',
      runningSince: '2026-11-07T11:45:00Z',
      change: { rollback: true, commits: [] },
    })

    assert.deepEqual(ofKind(body, 'change.failure'), [
      { 'event.name': 'change.failure', failed_version: 'v2', reason: 'rollback' },
    ])
    assert.deepEqual(ofKind(body, 'restore'), [
      { 'event.name': 'restore', version: 'v2', restore_seconds: 1200 },
    ])
  })

  it('fails a rolled back version without a restore time when it is not known when it started', () => {
    const body = deploymentEvents({
      ...deployment,
      running: 'v2',
      change: { rollback: true, commits: [] },
    })

    assert.equal(ofKind(body, 'change.failure').length, 1)
    assert.deepEqual(ofKind(body, 'restore'), [])
  })
})

describe('the command', () => {
  // The pipeline has no collector to send to, so it keeps the events, for github's record of the
  // deployment.
  it('writes the events to a file instead of sending them, when told to', () => {
    const out = join(repository, '..', `events-${Date.now()}.json`)
    execFileSync(process.execPath, [
      fileURLToPath(new URL('./deployment-event.mjs', import.meta.url)),
      ...['--environment', 'main', '--repository', repository],
      ...['--version', shas.third, '--running', shas.first, '--out', out],
    ])

    const body = JSON.parse(readFileSync(out, 'utf8'))
    rmSync(out)
    assert.equal(ofKind(body, 'deployment')[0].version, shas.third)
    assert.deepEqual(
      ofKind(body, 'deployment.commit').map(({ commit }) => commit),
      [shas.third, shas.second],
    )
  })
})
