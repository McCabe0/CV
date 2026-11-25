// The load test, run by k6 through ci/perf.sh: see decisions/performance.md. The jobs are seeded first
// (jobs.sql). Setup makes the candidates and their profiles, a CV request and a fit summary each, then
// adds a real network's delay to the database, then requests go at a fixed rate, which does not ease off
// when the api slows down. Keycloak is only used before they start: the api checks tokens against its
// cached keys. No CV is written and no fit summary asked for during the run: that is the LLM's work, on
// the cv-worker, which this stack does not run.
import { check } from 'k6'
import exec from 'k6/execution'
import http from 'k6/http'

const apiUrl = __ENV.PERF_API_URL || 'http://localhost:8080/api'
const keycloakUrl = __ENV.PERF_KEYCLOAK_URL || 'http://localhost:8180'
const REALM = 'skill2career'
// The test realm's public client that allows the password grant: the api's own tests use it.
const CLIENT = 'cv-api'
const PASSWORD = 'test'

const number = (name: string, fallback: number) => Number(__ENV[name] || fallback)
const RATE = number('PERF_RATE', 50)
const CANDIDATES = number('PERF_CANDIDATES', 20)
const DB_LATENCY_MS = number('PERF_DB_LATENCY_MS', 1)
const P99_TARGET_MS = number('PERF_P99_TARGET_MS', 200)

// `step` looks for the ceiling, a rate at a time, and the default holds one rate.
const STEP = __ENV.PERF_PROFILE === 'step'
const STEP_RATES = (__ENV.PERF_STEPS || '25,50,100,150,200').split(',').map(Number)
const STEP_SECONDS = number('PERF_STEP_SECONDS', 120)

const ENDPOINTS = [
  'matches',
  'matches remote',
  'matches near',
  'profile',
  'save profile',
  'cv request',
  'fit summary',
  'companies',
]

// Only here so that k6 keeps a separate figure for each: they can never fail.
const breakdown = (metric: string, tag: string, values: (string | number)[]) =>
  Object.fromEntries(
    values.map((value) => [
      `${metric}{${tag}:${value}}`,
      [metric === 'http_req_failed' ? 'rate>=0' : 'max>=0'],
    ]),
  )

export const options = {
  setupTimeout: '5m',
  summaryTrendStats: ['count', 'med', 'max', 'p(99)'],
  scenarios: {
    users: {
      executor: 'ramping-arrival-rate',
      timeUnit: '1s',
      startRate: 0,
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: STEP
        ? STEP_RATES.flatMap((target) => [
            { duration: '20s', target },
            { duration: `${STEP_SECONDS - 20}s`, target },
          ])
        : [
            { duration: '30s', target: RATE },
            { duration: '3m', target: RATE },
            { duration: '15s', target: 0 },
          ],
    },
  },
  thresholds: {
    // Setting up is not part of the result, so these are the scenario's requests.
    'http_req_failed{scenario:users}': [
      // No point paying for the steps above the one that broke.
      STEP ? { threshold: 'rate<0.2', abortOnFail: true, delayAbortEval: '30s' } : 'rate<0.01',
    ],
    // The p99 is a warning, not a failure: a shared runner moves it. handleSummary says when it is over
    // the target, and the job warns. This only makes k6 work it out.
    'http_req_duration{scenario:users}': ['max>=0'],
    // Requests it could not start, as every user was already waiting on the api.
    dropped_iterations: [STEP ? 'count>=0' : 'count<100'],
    ...breakdown('http_req_duration', 'name', ENDPOINTS),
    ...(STEP
      ? {
          ...breakdown('http_req_duration', 'rate', STEP_RATES),
          ...breakdown('http_req_failed', 'rate', STEP_RATES),
        }
      : {}),
  },
}

const json = (token?: string) => ({
  'Content-Type': 'application/json',
  ...(token ? { Authorization: `Bearer ${token}` } : {}),
})

const must = (response: { status: number; body: unknown }, ok: number[], what: string) => {
  if (!ok.includes(response.status)) throw new Error(`${what}: ${response.status} ${response.body}`)
  return response
}

const adminToken = () =>
  must(
    http.post(`${keycloakUrl}/realms/master/protocol/openid-connect/token`, {
      grant_type: 'password',
      client_id: 'admin-cli',
      username: __ENV.KEYCLOAK_ADMIN_USERNAME || 'admin',
      password: __ENV.KEYCLOAK_ADMIN_PASSWORD || 'admin',
    }),
    [200],
    "signing in to keycloak's admin api",
  ).json('access_token') as string

// The same as e2e/zap-auth.mts: the realm's 5 minute tokens would run out during a run, and the realm
// file is shared with the api's tests, so only the cv-api client gets an hour, on the running stack.
function lengthenTokens(admin: string) {
  const clients = `${keycloakUrl}/admin/realms/${REALM}/clients`
  const [client] = http.get(`${clients}?clientId=${CLIENT}`, { headers: json(admin) }).json() as {
    id: string
    attributes?: Record<string, string>
  }[]
  must(
    http.put(
      `${clients}/${client.id}`,
      JSON.stringify({
        ...client,
        attributes: { ...client.attributes, 'access.token.lifespan': '3600' },
      }),
      { headers: json(admin) },
    ),
    [204],
    `lengthening ${CLIENT}'s tokens`,
  )
}

// The candidates, made once on a stack and kept: a second run finds them there.
function makeCandidate(admin: string, username: string) {
  const users = `${keycloakUrl}/admin/realms/${REALM}/users`
  must(
    http.post(
      users,
      JSON.stringify({
        username,
        email: `${username}@example.com`,
        emailVerified: true,
        firstName: 'Perf',
        lastName: username,
        enabled: true,
        credentials: [{ type: 'password', value: PASSWORD, temporary: false }],
      }),
      { headers: json(admin) },
    ),
    [201, 409],
    `making ${username}`,
  )
  const [user] = http
    .get(`${users}?username=${username}&exact=true`, { headers: json(admin) })
    .json() as { id: string }[]
  const role = http.get(`${keycloakUrl}/admin/realms/${REALM}/roles/CANDIDATE`, {
    headers: json(admin),
  })
  must(
    http.post(`${users}/${user.id}/role-mappings/realm`, JSON.stringify([role.json()]), {
      headers: json(admin),
    }),
    [204],
    `giving ${username} the candidate role`,
  )
}

const signIn = (username: string) =>
  must(
    http.post(`${keycloakUrl}/realms/${REALM}/protocol/openid-connect/token`, {
      grant_type: 'password',
      client_id: CLIENT,
      username,
      password: PASSWORD,
    }),
    [200],
    `signing in as ${username}`,
  ).json('access_token') as string

// The same skills jobs.sql draws on, so every candidate matches some jobs, and each a different set.
const SKILLS = [
  'Kotlin', 'Java', 'Spring', 'SQL', 'PostgreSQL', 'Kafka', 'Docker', 'Kubernetes', 'AWS', 'GCP',
  'Terraform', 'TypeScript', 'React', 'Next.js', 'Node.js', 'Python', 'Go', 'Rust', 'GraphQL', 'Redis',
  'Elasticsearch', 'Linux', 'CI/CD', 'Observability', 'Scala', 'Swift', 'Android', 'Machine Learning',
  'Airflow', 'dbt',
]

const profileOf = (candidate: number) => ({
  name: `Perf Candidate ${candidate}`,
  skills: Array.from({ length: 6 }, (_, i) => SKILLS[(candidate * 7 + i * 5) % SKILLS.length]),
  experience: 'Eight years building and running web services, most recently leading a platform team.',
  education: 'BSc Computer Science',
  targetRole: 'Senior Software Engineer',
  location: candidate % 2 === 0 ? 'London' : 'Manchester',
  workHistory: [
    {
      company: 'Acme',
      title: 'Software Engineer',
      startDate: '2019-01',
      bullets: ['Built the payments api', 'Ran the on call rota'],
    },
  ],
})

type Candidate = { token: string; profile: ReturnType<typeof profileOf>; cvRequest: number; job: number }

export function setup() {
  const admin = adminToken()
  lengthenTokens(admin)
  const candidates: Candidate[] = []
  for (let i = 1; i <= CANDIDATES; i++) {
    const username = `perf-candidate-${String(i).padStart(2, '0')}`
    makeCandidate(admin, username)
    const token = signIn(username)
    const profile = profileOf(i)
    must(
      http.put(`${apiUrl}/profile`, JSON.stringify(profile), { headers: json(token) }),
      [200],
      `saving ${username}'s profile`,
    )
    const matches = must(
      http.get(`${apiUrl}/jobs/matches`, { headers: json(token) }),
      [200],
      `${username}'s matches`,
    ).json() as { id: number }[]
    if (matches.length === 0) throw new Error(`${username} matches no jobs: were they seeded?`)
    const job = matches[0].id
    const cvRequest = must(
      http.post(`${apiUrl}/cv-requests`, null, { headers: json(token) }),
      [202],
      `${username}'s cv request`,
    ).json('id') as number
    must(
      http.post(`${apiUrl}/jobs/${job}/fit`, null, { headers: json(token) }),
      [200, 202],
      `${username}'s fit summary`,
    )
    candidates.push({ token, profile, cvRequest, job })
  }
  addDbLatency()
  return { candidates, admin: signIn('admin') }
}

// A real database is over a network, and here it is on the same machine. After setting up, as that is
// not what is measured. Each request waits on this as many times as it queries.
function addDbLatency() {
  const proxy = __ENV.PERF_TOXIPROXY_URL
  if (!proxy) return
  // A run that ended early leaves its latency on a stack that is kept.
  must(http.del(`${proxy}/proxies/db/toxics/latency`), [204, 404], 'removing the last latency')
  must(
    http.post(
      `${proxy}/proxies/db/toxics`,
      JSON.stringify({
        name: 'latency',
        type: 'latency',
        stream: 'downstream',
        attributes: { latency: DB_LATENCY_MS },
      }),
      { headers: json() },
    ),
    [200],
    'adding the database latency',
  )
}

type Data = ReturnType<typeof setup>

function get(name: string, path: string, token: string) {
  const response = http.get(`${apiUrl}${path}`, { headers: json(token), tags: { name } })
  check(response, { [`${name} ok`]: (r) => r.status === 200 })
}

// One request each time, in the proportions a day's use makes them: mostly looking for jobs. Saving a
// profile is the only write, and saves the same one, so the data stays the same through the run.
export default function ({ candidates, admin }: Data) {
  if (STEP) {
    const elapsed = Date.now() - exec.scenario.startTime
    const step = Math.min(Math.floor(elapsed / (STEP_SECONDS * 1000)), STEP_RATES.length - 1)
    exec.vu.metrics.tags.rate = String(STEP_RATES[step])
  }
  const candidate = candidates[Math.floor(Math.random() * candidates.length)]
  const roll = Math.random() * 100
  if (roll < 30) get('matches', '/jobs/matches', candidate.token)
  else if (roll < 42) get('matches remote', '/jobs/matches?remoteOnly=true', candidate.token)
  else if (roll < 54) get('matches near', '/jobs/matches?location=London', candidate.token)
  else if (roll < 74) get('profile', '/profile', candidate.token)
  else if (roll < 75) {
    const response = http.put(`${apiUrl}/profile`, JSON.stringify(candidate.profile), {
      headers: json(candidate.token),
      tags: { name: 'save profile' },
    })
    check(response, { 'save profile ok': (r) => r.status === 200 })
  } else if (roll < 85) get('cv request', `/cv-requests/${candidate.cvRequest}`, candidate.token)
  else if (roll < 95) get('fit summary', `/jobs/${candidate.job}/fit`, candidate.token)
  else get('companies', '/admin/companies', admin)
}

const ms = (value?: number) => (value === undefined ? '-' : `${Math.round(value)}ms`)

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function handleSummary(data: any) {
  const row = (label: string, metric: string) => {
    const { values } = data.metrics[metric] ?? {}
    return values && values.count !== 0
      ? `| ${label} | ${values.count ?? '-'} | ${ms(values.med)} | ${ms(values['p(99)'])} | ${ms(values.max)} |`
      : `| ${label} | 0 | - | - | - |`
  }
  const failed = (metric: string) => data.metrics[metric]?.values.rate ?? 0
  const percent = (rate: number) => `${(rate * 100).toFixed(2)}%`
  const p99 = data.metrics['http_req_duration{scenario:users}']?.values['p(99)'] ?? 0
  const dropped = data.metrics.dropped_iterations?.values.count ?? 0
  const over = !STEP && p99 > P99_TARGET_MS
  const lines = [
    `### Load test: ${STEP ? `steps of ${STEP_RATES.join(', ')} requests a second` : `${RATE} requests a second`}, ${CANDIDATES} candidates, ${DB_LATENCY_MS}ms database latency`,
    '',
    `p99 ${ms(p99)} against a target of ${P99_TARGET_MS}ms${over ? ', over it: a warning, not a failure' : ''}. Failed requests ${percent(failed('http_req_failed{scenario:users}'))}, ${dropped} could not start.`,
    '',
    '| endpoint | requests | median | p99 | max |',
    '|---|---|---|---|---|',
    ...ENDPOINTS.map((name) => row(name, `http_req_duration{name:${name}}`)),
    '',
  ]
  if (STEP) {
    lines.push(
      '| rate | requests | median | p99 | max | failed |',
      '|---|---|---|---|---|---|',
      ...STEP_RATES.map(
        (rate) =>
          `${row(String(rate), `http_req_duration{rate:${rate}}`)} ${percent(failed(`http_req_failed{rate:${rate}}`))} |`,
      ),
      '',
    )
  }
  const text = `${lines.join('\n')}\n`
  return {
    stdout: text,
    '/report/summary.md': text,
    '/report/summary.json': JSON.stringify(data),
    // For the job, which warns when it is over.
    '/report/p99.json': JSON.stringify({ p99: Math.round(p99), target: P99_TARGET_MS, over }),
  }
}
