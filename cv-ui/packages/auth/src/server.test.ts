import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'
import { authConfig } from './config'
import { getSession, requireSession } from './server'
import { sealSession, SESSION_COOKIE, type Session } from './session'

jest.mock('next/headers', () => ({ cookies: jest.fn() }))
jest.mock('next/navigation', () => ({
  redirect: jest.fn(() => {
    throw new Error('NEXT_REDIRECT')
  }),
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
