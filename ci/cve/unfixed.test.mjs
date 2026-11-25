// node --test ci/cve
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { summary, unfixed } from './unfixed.mjs'

const match = (id, severity, state, name = 'openssl', version = '3.5.1') => ({
  vulnerability: { id, severity, fix: { state } },
  artifact: { name, version },
})

test('keeps only the high and critical vulnerabilities that have no fix, once each', () => {
  const rows = unfixed([
    {
      image: 'api',
      matches: [
        match('CVE-1', 'High', 'not-fixed'),
        match('CVE-2', 'Critical', 'fixed'),
        match('CVE-3', 'Medium', 'not-fixed'),
        match('CVE-4', 'Critical', 'wont-fix', 'zlib', '1.3'),
      ],
    },
    { image: 'shell', matches: [match('CVE-1', 'High', 'not-fixed', 'openssl', '3.5.2')] },
  ])

  assert.deepEqual(
    rows.map(({ id, severity, packages, images }) => [id, severity, [...packages], [...images]]),
    [
      ['CVE-4', 'Critical', ['zlib 1.3'], ['api']],
      ['CVE-1', 'High', ['openssl 3.5.1', 'openssl 3.5.2'], ['api', 'shell']],
    ],
  )
})

test('counts a vulnerability with no fix state at all as having no fix', () => {
  const rows = unfixed([
    { image: 'api', matches: [{ vulnerability: { id: 'CVE-5', severity: 'High' }, artifact: { name: 'x', version: '1' } }] },
  ])

  assert.equal(rows.length, 1)
})

test('says so when there are none, and lists them in a table when there are', () => {
  assert.equal(summary([]), 'No high or critical vulnerabilities without a fix.\n')
  assert.equal(
    summary(unfixed([{ image: 'api', matches: [match('CVE-1', 'High', 'not-fixed')] }])),
    '| vulnerability | severity | packages | images |\n|---|---|---|---|\n| CVE-1 | High | openssl 3.5.1 | api |\n',
  )
})
