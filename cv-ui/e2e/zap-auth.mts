// Signs in for the OWASP ZAP scans (ci/zap.sh), on the throwaway stack, and prints one value for ZAP to
// send on every request:
//   node e2e/zap-auth.mts token <user>          a bearer token for the api
//   node e2e/zap-auth.mts session <user> <path> the ui's session cookie, signed in on keycloak's own page
// See decisions/zap.md.
import { chromium } from '@playwright/test'

const uiUrl = process.env.ZAP_UI_URL ?? 'http://localhost:3000'
const keycloakUrl = process.env.ZAP_KEYCLOAK_URL ?? 'http://localhost:8180'
const REALM = 'skill2career'
// The test realm's public client that allows the password grant: the api's own tests use it.
const CLIENT = 'cv-api'
const PASSWORD = 'test'

// The api scan runs for longer than the test realm's 5 minute tokens, and ZAP cannot refresh one, so it
// would be refused part way through. The realm file is shared with the api's tests, so instead this gives
// only the cv-api client an hour, on the running stack, through keycloak's admin api. The stack's
// bootstrap admin is set in docker-compose.yml.
const SCAN_TOKEN_LIFESPAN_SECONDS = 3600

const form = (fields: Record<string, string>) => new URLSearchParams(fields)

async function adminHeaders() {
  const response = await fetch(`${keycloakUrl}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    body: form({
      grant_type: 'password',
      client_id: 'admin-cli',
      username: process.env.KEYCLOAK_ADMIN_USERNAME ?? 'admin',
      password: process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin',
    }),
  })
  if (!response.ok) throw new Error(`could not sign in to keycloak's admin api: ${response.status}`)
  const { access_token } = (await response.json()) as { access_token: string }
  return { Authorization: `Bearer ${access_token}` }
}

async function lengthenTokens() {
  const headers = await adminHeaders()
  const clients = `${keycloakUrl}/admin/realms/${REALM}/clients`
  const [client] = (await (await fetch(`${clients}?clientId=${CLIENT}`, { headers })).json()) as {
    id: string
    attributes?: Record<string, string>
  }[]
  if (!client) throw new Error(`no ${CLIENT} client in ${REALM}`)

  const updated = await fetch(`${clients}/${client.id}`, {
    method: 'PUT',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      ...client,
      attributes: {
        ...client.attributes,
        'access.token.lifespan': String(SCAN_TOKEN_LIFESPAN_SECONDS),
      },
    }),
  })
  if (!updated.ok) throw new Error(`could not lengthen ${CLIENT}'s tokens: ${updated.status}`)
}

async function token(username: string) {
  await lengthenTokens()
  const response = await fetch(`${keycloakUrl}/realms/${REALM}/protocol/openid-connect/token`, {
    method: 'POST',
    body: form({ grant_type: 'password', client_id: CLIENT, username, password: PASSWORD }),
  })
  if (!response.ok) throw new Error(`could not get ${username}'s token: ${response.status}`)
  return ((await response.json()) as { access_token: string }).access_token
}

async function session(username: string, path: string) {
  const browser = await chromium.launch()
  try {
    const page = await browser.newPage()
    await page.goto(`${uiUrl}${path}`)
    await page.getByLabel('Username or email').fill(username)
    await page.getByLabel('Password', { exact: true }).fill(PASSWORD)
    await page.getByRole('button', { name: 'Sign In' }).click()
    await page.waitForURL(
      (url) => url.origin === new URL(uiUrl).origin && url.pathname.startsWith(path),
    )
    const cookie = (await page.context().cookies()).find(({ name }) => name === 'cv_session')
    if (!cookie) throw new Error(`${username} has no session after signing in`)
    return `${cookie.name}=${cookie.value}`
  } finally {
    await browser.close()
  }
}

const [command, username, path] = process.argv.slice(2)
if (command === 'token' && username) {
  console.log(await token(username))
} else if (command === 'session' && username && path) {
  console.log(await session(username, path))
} else {
  console.error('usage: node e2e/zap-auth.mts token <user> | session <user> <path>')
  process.exit(2)
}
