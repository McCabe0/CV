import { NextRequest } from 'next/server'
import { authConfig } from './config'
import { refreshSession } from './oidc'
import { sessionProxy } from './proxy'
import { sealSession, SESSION_COOKIE, unsealSession, type Session } from './session'

jest.mock('./oidc', () => ({ refreshSession: jest.fn() }))

const config = authConfig({
  KEYCLOAK_ISSUER: 'https://id.example.com/realms/skill2career',
  KEYCLOAK_CLIENT_ID: 'cv-ui',
  KEYCLOAK_CLIENT_SECRET: 'secret',
  APP_URL: 'https://cv.example.com',
  SESSION_SECRET: 'x'.repeat(32),
})

const now = () => Math.floor(Date.now() / 1000)

const session = (expiresAt: number, roles = ['CANDIDATE']): Session => ({
  user: { username: 'casey', name: 'Casey', roles },
  accessToken: 'a',
  refreshToken: 'r',
  expiresAt,
})

const request = async (path: string, current?: Session) => {
  const req = new NextRequest(new URL(path, 'https://cv.example.com'))
  if (current) req.cookies.set(SESSION_COOKIE, await sealSession(current, config.sessionSecret))
  return req
}

beforeEach(() => jest.spyOn(console, 'error').mockImplementation(() => {}))

describe('sessionProxy', () => {
  it('passes a fresh session through untouched', async () => {
    const response = await sessionProxy(await request('/profile', session(now() + 600)), config, {
      requireSignIn: true,
    })

    expect(response.headers.get('x-middleware-next')).toBe('1')
    expect(refreshSession).not.toHaveBeenCalled()
    expect(response.cookies.get(SESSION_COOKIE)).toBeUndefined()
  })

  it('sends a signed out user to sign in, and back here afterwards', async () => {
    const response = await sessionProxy(await request('/profile?tab=work'), config, {
      requireSignIn: true,
    })

    expect(response.headers.get('location')).toBe(
      'https://cv.example.com/auth/login?returnTo=%2Fprofile%3Ftab%3Dwork',
    )
  })

  it('lets a signed out user through a page that does not need sign in', async () => {
    const response = await sessionProxy(await request('/'), config)

    expect(response.headers.get('x-middleware-next')).toBe('1')
  })

  it('refreshes a session about to expire, for the page and the browser', async () => {
    const fresh = session(now() + 300)
    jest.mocked(refreshSession).mockResolvedValue(fresh)

    const response = await sessionProxy(await request('/profile', session(now() + 10)), config, {
      requireSignIn: true,
    })

    expect(
      await unsealSession(response.cookies.get(SESSION_COOKIE)!.value, config.sessionSecret),
    ).toEqual(fresh)
    expect(response.headers.get('x-middleware-request-cookie')).toContain(SESSION_COOKIE)
  })

  it('signs the user out when the session can no longer be refreshed', async () => {
    jest.mocked(refreshSession).mockRejectedValue(new Error('session ended in keycloak'))

    const response = await sessionProxy(await request('/profile', session(now() - 10)), config, {
      requireSignIn: true,
    })

    expect(response.headers.get('location')).toBe(
      'https://cv.example.com/auth/login?returnTo=%2Fprofile',
    )
    expect(response.cookies.get(SESSION_COOKIE)!.value).toBe('')
  })

  it('shows forbidden, with a 403, to a user without the role', async () => {
    const response = await sessionProxy(
      await request('/admin', session(now() + 600, ['CANDIDATE'])),
      config,
      {
        requireSignIn: true,
        roles: ['ADMIN'],
      },
    )

    expect(response.status).toBe(403)
    expect(response.headers.get('x-middleware-rewrite')).toBe('https://cv.example.com/forbidden')
  })

  it('lets a user with the role through', async () => {
    const response = await sessionProxy(
      await request('/admin', session(now() + 600, ['ADMIN'])),
      config,
      {
        requireSignIn: true,
        roles: ['ADMIN'],
      },
    )

    expect(response.status).toBe(200)
  })
})

describe('content security policy', () => {
  const policy = (response: Response) => response.headers.get('content-security-policy') ?? ''
  /** The policy the page sees on this same request, where next finds the nonce for its scripts. */
  const forwardedPolicy = (response: Response) =>
    response.headers.get('x-middleware-request-content-security-policy')

  it('is set on the page and the response, with a new nonce each time', async () => {
    const first = await sessionProxy(await request('/'), config)
    const second = await sessionProxy(await request('/'), config)

    expect(policy(first)).toMatch(/script-src 'self' 'nonce-[A-Za-z0-9+/=]+' 'strict-dynamic';/)
    expect(forwardedPolicy(first)).toBe(policy(first))
    expect(policy(first)).not.toBe(policy(second))
  })

  it('lets forms post to keycloak, as signing out redirects there', async () => {
    const response = await sessionProxy(await request('/'), config)

    expect(policy(response)).toContain("form-action 'self' https://id.example.com;")
  })

  it('is passed on to a refused page too', async () => {
    const response = await sessionProxy(await request('/admin', session(now() + 600)), config, {
      requireSignIn: true,
      roles: ['ADMIN'],
    })

    expect(forwardedPolicy(response)).toBe(policy(response))
    expect(policy(response)).toContain("default-src 'self'")
  })
})
