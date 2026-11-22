export type AuthConfig = {
  issuer: URL
  /** Where this server calls keycloak when that differs from the public issuer, eg between containers. */
  internalUrl?: URL
  clientId: string
  clientSecret: string
  /** The shell's public url: keycloak redirects back to it. */
  appUrl: string
  sessionSecret: string
  /** Only for a local keycloak served over plain http. */
  allowHttp: boolean
  secureCookies: boolean
}

type Env = Record<string, string | undefined>

const required = [
  'KEYCLOAK_ISSUER',
  'KEYCLOAK_CLIENT_ID',
  'KEYCLOAK_CLIENT_SECRET',
  'APP_URL',
  'SESSION_SECRET',
] as const

/** The settings, read once per call, so a bad deploy fails at its first request with every gap named. */
export function authConfig(env: Env = process.env): AuthConfig {
  const missing = required.filter((name) => !env[name])
  if (missing.length > 0) throw new Error(`Missing auth settings: ${missing.join(', ')}`)
  const value = (name: (typeof required)[number]) => env[name] as string

  const issuer = new URL(value('KEYCLOAK_ISSUER'))
  const allowHttp = env.KEYCLOAK_ALLOW_HTTP === 'true'
  if (issuer.protocol !== 'https:' && !allowHttp) {
    throw new Error(
      'KEYCLOAK_ISSUER must use https (set KEYCLOAK_ALLOW_HTTP=true for a local keycloak)',
    )
  }

  return {
    issuer,
    ...(env.KEYCLOAK_INTERNAL_URL && { internalUrl: new URL(env.KEYCLOAK_INTERNAL_URL) }),
    clientId: value('KEYCLOAK_CLIENT_ID'),
    clientSecret: value('KEYCLOAK_CLIENT_SECRET'),
    appUrl: value('APP_URL'),
    sessionSecret: value('SESSION_SECRET'),
    allowHttp,
    secureCookies: new URL(value('APP_URL')).protocol === 'https:',
  }
}
