import { NextRequest } from 'next/server'
import { authConfig } from './config'
import { LOGIN_COOKIE } from './cookies'
import { callback, login, logout } from './handlers'
import { beginLogin, completeLogin, endSession } from './oidc'
import { seal, sealSession, SESSION_COOKIE, unsealSession, type Session } from './session'

jest.mock('./oidc', () => ({
  ...jest.requireActual('./oidc'),
  beginLogin: jest.fn(),
  completeLogin: jest.fn(),
  endSession: jest.fn(),
}))

const config = authConfig({
  KEYCLOAK_ISSUER: 'https://id.example.com/realms/skill2career',
  KEYCLOAK_CLIENT_ID: 'cv-ui',
  KEYCLOAK_CLIENT_SECRET: 'secret',
  APP_URL: 'https://cv.example.com',
  SESSION_SECRET: 'x'.repeat(32),
})

const session: Session = {
  user: { username: 'casey', name: 'Casey', roles: ['CANDIDATE'] },
  accessToken: 'a',
  refreshToken: 'r',
  expiresAt: 0,
}

const request = (
  path: string,
  init: {
    cookies?: Record<string, string>
    headers?: Record<string, string>
    method?: string
  } = {},
) => {
  const req = new NextRequest(new URL(path, 'https://cv.example.com'), {
    method: init.method ?? 'GET',
    headers: init.headers ?? {},
  })
  Object.entries(init.cookies ?? {}).forEach(([name, value]) => req.cookies.set(name, value))
  return req
}

beforeEach(() => {
  jest.spyOn(console, 'error').mockImplementation(() => {})
  jest.mocked(beginLogin).mockResolvedValue({
    url: new URL('https://id.example.com/auth'),
    verifier: 'v',
    state: 's',
  })
})

describe('login', () => {
  it('sends the user to keycloak, keeping where to return to, sealed', async () => {
    const response = await login(request('/auth/login?returnTo=/profile'), config)

    expect(response.headers.get('location')).toBe('https://id.example.com/auth')
    const cookie = response.cookies.get(LOGIN_COOKIE)!
    expect(cookie.httpOnly).toBe(true)
    expect(cookie.secure).toBe(true)
  })

  it.each(['https://evil.example.com', '//evil.example.com', '/\\evil.example.com'])(
    'never returns the user to another site: %s',
    async (returnTo) => {
      jest.mocked(completeLogin).mockResolvedValue(session)
      const started = await login(
        request(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`),
        config,
      )

      const finished = await callback(
        request('/auth/callback?code=1', {
          cookies: { [LOGIN_COOKIE]: started.cookies.get(LOGIN_COOKIE)!.value },
        }),
        config,
      )

      expect(finished.headers.get('location')).toBe('https://cv.example.com/')
    },
  )
})

describe('callback', () => {
  it('signs the user in, and returns them where they were going', async () => {
    jest.mocked(completeLogin).mockResolvedValue(session)
    const pending = await seal(
      { verifier: 'v', state: 's', returnTo: '/profile' },
      config.sessionSecret,
    )

    const response = await callback(
      request('/auth/callback?code=1&state=s', { cookies: { [LOGIN_COOKIE]: pending } }),
      config,
    )

    expect(response.headers.get('location')).toBe('https://cv.example.com/profile')
    expect(
      await unsealSession(response.cookies.get(SESSION_COOKIE)!.value, config.sessionSecret),
    ).toEqual(session)
    expect(response.cookies.get(LOGIN_COOKIE)!.value).toBe('')
    expect(jest.mocked(completeLogin).mock.calls[0]![1].href).toBe(
      'https://cv.example.com/auth/callback?code=1&state=s',
    )
  })

  it('sends the user to the error page without a login in progress', async () => {
    const response = await callback(request('/auth/callback?code=1'), config)

    expect(response.headers.get('location')).toBe('https://cv.example.com/auth/error')
    expect(completeLogin).not.toHaveBeenCalled()
  })

  it('sends the user to the error page when keycloak refuses the code', async () => {
    jest.mocked(completeLogin).mockRejectedValue(new Error('invalid_grant'))
    const pending = await seal({ verifier: 'v', state: 's', returnTo: '/' }, config.sessionSecret)

    const response = await callback(
      request('/auth/callback?code=1', { cookies: { [LOGIN_COOKIE]: pending } }),
      config,
    )

    expect(response.headers.get('location')).toBe('https://cv.example.com/auth/error')
    expect(response.cookies.get(SESSION_COOKIE)).toBeUndefined()
  })
})

describe('logout', () => {
  it('ends the keycloak session and clears ours', async () => {
    jest.mocked(endSession).mockResolvedValue()
    const sealed = await sealSession(session, config.sessionSecret)

    const response = await logout(
      request('/auth/logout', {
        method: 'POST',
        cookies: { [SESSION_COOKIE]: sealed },
        headers: { origin: 'https://cv.example.com' },
      }),
      config,
    )

    expect(response.status).toBe(303)
    expect(response.headers.get('location')).toBe('https://cv.example.com/')
    expect(endSession).toHaveBeenCalledWith(config, session)
    expect(response.cookies.get(SESSION_COOKIE)!.value).toBe('')
  })

  it('signs out even when keycloak cannot be reached', async () => {
    jest.mocked(endSession).mockRejectedValue(new Error('down'))
    const sealed = await sealSession(session, config.sessionSecret)

    const response = await logout(
      request('/auth/logout', { method: 'POST', cookies: { [SESSION_COOKIE]: sealed } }),
      config,
    )

    expect(response.status).toBe(303)
    expect(response.cookies.get(SESSION_COOKIE)!.value).toBe('')
  })

  it('clears nothing in keycloak without a session', async () => {
    await logout(request('/auth/logout', { method: 'POST' }), config)

    expect(endSession).not.toHaveBeenCalled()
  })

  it('refuses a sign out posted from another site', async () => {
    const response = await logout(
      request('/auth/logout', { method: 'POST', headers: { origin: 'https://evil.example.com' } }),
      config,
    )

    expect(response.status).toBe(403)
  })
})
