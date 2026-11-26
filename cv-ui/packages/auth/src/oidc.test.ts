import * as client from 'openid-client'
import { authConfig } from './config'
import { beginLogin, completeLogin, endSession, refreshSession } from './oidc'

jest.mock('openid-client', () => ({
  discovery: jest.fn(),
  buildAuthorizationUrl: jest.fn(),
  authorizationCodeGrant: jest.fn(),
  refreshTokenGrant: jest.fn(),
  randomPKCECodeVerifier: () => 'verifier',
  randomState: () => 'state',
  calculatePKCECodeChallenge: async () => 'challenge',
  allowInsecureRequests: 'allowInsecureRequests',
  customFetch: Symbol('customFetch'),
}))

const mocked = jest.mocked(client)

const config = (env: Record<string, string> = {}) =>
  authConfig({
    KEYCLOAK_ISSUER: `https://id.example.com/realms/${Math.random()}`,
    KEYCLOAK_CLIENT_ID: 'cv-ui',
    KEYCLOAK_CLIENT_SECRET: 'secret',
    APP_URL: 'https://cv.example.com',
    SESSION_SECRET: 'x'.repeat(32),
    ...env,
  })

const accessToken = `${btoa('{}')}.${btoa(JSON.stringify({ preferred_username: 'casey', roles: ['CANDIDATE'] }))}.sig`

// Stands in for both the code and the refresh grant's result, which openid-client brands differently.
const tokens = (refreshToken?: string) =>
  ({
    access_token: accessToken,
    refresh_token: refreshToken,
    expiresIn: () => 300,
  }) as never

const keycloak = {
  serverMetadata: () => ({ end_session_endpoint: 'https://id.example.com/logout' }),
} as unknown as client.Configuration

beforeEach(() => {
  mocked.discovery.mockResolvedValue(keycloak)
})

describe('beginLogin', () => {
  it('sends the user to keycloak with pkce, and back to the shell', async () => {
    mocked.buildAuthorizationUrl.mockReturnValue(new URL('https://id.example.com/auth'))

    const login = await beginLogin(config())

    expect(login).toEqual({
      url: new URL('https://id.example.com/auth'),
      verifier: 'verifier',
      state: 'state',
    })
    expect(mocked.buildAuthorizationUrl).toHaveBeenCalledWith(keycloak, {
      redirect_uri: 'https://cv.example.com/auth/callback',
      scope: 'openid',
      code_challenge: 'challenge',
      code_challenge_method: 'S256',
      state: 'state',
    })
  })

  it('tries discovery again after it failed', async () => {
    const settings = config()
    mocked.discovery.mockRejectedValueOnce(new Error('keycloak down'))

    await expect(beginLogin(settings)).rejects.toThrow('keycloak down')
    await beginLogin(settings)

    expect(mocked.discovery).toHaveBeenCalledTimes(2)
  })

  it('allows a local keycloak over http, reached at its internal address', async () => {
    const fetchSpy = jest.spyOn(global, 'fetch').mockResolvedValue(new Response('{}'))
    await beginLogin(
      config({
        KEYCLOAK_ISSUER: 'http://localhost:8180/realms/local',
        KEYCLOAK_ALLOW_HTTP: 'true',
        KEYCLOAK_INTERNAL_URL: 'http://keycloak:8080',
      }),
    )

    const options = mocked.discovery.mock.calls[0]![4] as Record<string | symbol, unknown>
    expect(options.execute).toEqual(['allowInsecureRequests'])
    const fetcher = options[client.customFetch] as (url: string, init: object) => Promise<unknown>
    await fetcher('http://localhost:8180/realms/local/.well-known', {})
    await fetcher('http://elsewhere.example.com/x', {})
    expect(fetchSpy).toHaveBeenNthCalledWith(1, 'http://keycloak:8080/realms/local/.well-known', {})
    expect(fetchSpy).toHaveBeenNthCalledWith(2, 'http://elsewhere.example.com/x', {})
    fetchSpy.mockRestore()
  })
})

describe('completeLogin', () => {
  it('makes a session from keycloak tokens', async () => {
    mocked.authorizationCodeGrant.mockResolvedValue(tokens('refresh'))
    const now = Math.floor(Date.now() / 1000)

    const session = await completeLogin(
      config(),
      new URL('https://cv.example.com/auth/callback?code=1'),
      {
        verifier: 'verifier',
        state: 'state',
      },
    )

    expect(session.user).toEqual({ username: 'casey', name: 'casey', roles: ['CANDIDATE'] })
    expect(session.refreshToken).toBe('refresh')
    expect(session.expiresAt).toBeGreaterThanOrEqual(now + 300)
  })

  it('fails without a refresh token, as the session could not be kept fresh', async () => {
    mocked.authorizationCodeGrant.mockResolvedValue(tokens(undefined))

    await expect(
      completeLogin(config(), new URL('https://cv.example.com/auth/callback'), {
        verifier: 'v',
        state: 's',
      }),
    ).rejects.toThrow('refresh token')
  })
})

describe('refreshSession', () => {
  it('keeps the old refresh token when keycloak sends none', async () => {
    mocked.refreshTokenGrant.mockResolvedValue(tokens(undefined))

    const session = await refreshSession(config(), {
      user: { username: 'casey', name: 'casey', roles: [] },
      accessToken: 'old',
      refreshToken: 'old-refresh',
      expiresAt: 0,
    })

    expect(session.accessToken).toBe(accessToken)
    expect(session.refreshToken).toBe('old-refresh')
  })
})

describe('endSession', () => {
  const session = {
    user: { username: 'casey', name: 'casey', roles: [] },
    accessToken: 'a',
    refreshToken: 'r',
    expiresAt: 0,
  }

  it('ends the keycloak session from the server', async () => {
    const fetchSpy = jest
      .spyOn(global, 'fetch')
      .mockResolvedValue(new Response(null, { status: 204 }))

    await endSession(config(), session)

    expect(fetchSpy).toHaveBeenCalledWith(
      'https://id.example.com/logout',
      expect.objectContaining({ method: 'POST' }),
    )
    fetchSpy.mockRestore()
  })

  it('fails when keycloak refuses', async () => {
    const fetchSpy = jest
      .spyOn(global, 'fetch')
      .mockResolvedValue(new Response(null, { status: 400 }))

    await expect(endSession(config(), session)).rejects.toThrow('400')
    fetchSpy.mockRestore()
  })

  it('fails when keycloak has no logout endpoint', async () => {
    mocked.discovery.mockResolvedValue({
      serverMetadata: () => ({}),
    } as unknown as client.Configuration)

    await expect(endSession(config(), session)).rejects.toThrow('no logout endpoint')
  })
})
