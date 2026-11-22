import { cookies, headers } from 'next/headers'
import { redirect } from 'next/navigation'
import { authConfig } from './config'
import { getSession, redirectInZone, requireSession } from './server'
import { sealSession, SESSION_COOKIE, type Session } from './session'

jest.mock('next/headers', () => ({ cookies: jest.fn(), headers: jest.fn() }))
jest.mock('next/navigation', () => ({
  redirect: jest.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT ${url}`)
  }),
}))

describe('redirectInZone', () => {
  const withHeaders = (entries: Record<string, string>) =>
    jest.mocked(headers).mockResolvedValue(new Headers(entries) as never)

  it('leaves the base path to next when javascript sent the form, as next adds it', async () => {
    withHeaders({ 'next-action': 'abc123' })

    await expect(redirectInZone('/cv', '/requests/7')).rejects.toThrow('NEXT_REDIRECT /requests/7')
  })

  it('adds the base path when the browser sent the form without javascript, as next does not', async () => {
    withHeaders({ 'content-type': 'application/x-www-form-urlencoded' })

    await expect(redirectInZone('/cv', '/requests/7')).rejects.toThrow(
      'NEXT_REDIRECT /cv/requests/7',
    )
  })
})

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

const withCookie = (value?: string) =>
  jest.mocked(cookies).mockResolvedValue({
    get: (name: string) => (name === SESSION_COOKIE && value ? { name, value } : undefined),
  } as unknown as Awaited<ReturnType<typeof cookies>>)

describe('getSession', () => {
  it('reads the session from its cookie', async () => {
    withCookie(await sealSession(session, config.sessionSecret))

    expect(await getSession(config)).toEqual(session)
  })

  it('is null when signed out', async () => {
    withCookie(undefined)

    expect(await getSession(config)).toBeNull()
  })

  it('reads the cookies before the settings, so next renders the page per request, never at build time', async () => {
    // At build time next makes cookies() throw, to mark the page dynamic. There are no settings then.
    jest.mocked(cookies).mockRejectedValue(new Error('Dynamic server usage: cookies'))
    const env = process.env
    process.env = {}

    await expect(getSession()).rejects.toThrow('Dynamic server usage')
    process.env = env
  })
})

describe('requireSession', () => {
  it('returns the session when signed in', async () => {
    withCookie(await sealSession(session, config.sessionSecret))

    expect(await requireSession('/profile', config)).toEqual(session)
  })

  it('sends a signed out user to the shell to sign in, and back afterwards', async () => {
    withCookie(undefined)

    await expect(requireSession('/profile', config)).rejects.toThrow('NEXT_REDIRECT')
    expect(redirect).toHaveBeenCalledWith('https://cv.example.com/auth/login?returnTo=%2Fprofile')
  })
})
