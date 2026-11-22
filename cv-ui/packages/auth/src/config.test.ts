import { authConfig } from './config'

const env = {
  KEYCLOAK_ISSUER: 'https://id.example.com/realms/skill2career',
  KEYCLOAK_CLIENT_ID: 'cv-ui',
  KEYCLOAK_CLIENT_SECRET: 'secret',
  APP_URL: 'https://cv.example.com',
  SESSION_SECRET: 'x'.repeat(32),
}

describe('authConfig', () => {
  it('reads the settings, with secure cookies on https', () => {
    const config = authConfig(env)

    expect(config.issuer.href).toBe(env.KEYCLOAK_ISSUER)
    expect(config.clientId).toBe('cv-ui')
    expect(config.secureCookies).toBe(true)
    expect(config.internalUrl).toBeUndefined()
  })

  it('names every missing setting, so a bad deploy fails at once', () => {
    expect(() => authConfig({ ...env, KEYCLOAK_CLIENT_ID: '', SESSION_SECRET: undefined })).toThrow(
      'Missing auth settings: KEYCLOAK_CLIENT_ID, SESSION_SECRET',
    )
  })

  it('refuses keycloak over http, unless allowed for a local one', () => {
    const local = { ...env, KEYCLOAK_ISSUER: 'http://localhost:8180/realms/skill2career' }

    expect(() => authConfig(local)).toThrow('must use https')
    expect(authConfig({ ...local, KEYCLOAK_ALLOW_HTTP: 'true' }).allowHttp).toBe(true)
  })

  it('keeps an internal keycloak url, and plain cookies on http', () => {
    const config = authConfig({
      ...env,
      APP_URL: 'http://localhost:3000',
      KEYCLOAK_INTERNAL_URL: 'http://keycloak:8080',
    })

    expect(config.internalUrl?.origin).toBe('http://keycloak:8080')
    expect(config.secureCookies).toBe(false)
  })
})
