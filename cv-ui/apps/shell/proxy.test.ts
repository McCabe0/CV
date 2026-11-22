/** @jest-environment @stryker-mutator/jest-runner/jest-env/node */
import { NextRequest, NextResponse } from 'next/server'
import { sessionProxy } from '@cv/auth/proxy'
import { config, proxy } from './proxy'

jest.mock('@cv/auth/proxy', () => ({ sessionProxy: jest.fn() }))

const request = (url: string) => new NextRequest(new URL(url, 'http://localhost:3000'))
const rewrittenTo = async (response: Promise<Response>) =>
  (await response).headers.get('x-middleware-rewrite')

describe('shell proxy', () => {
  const env = process.env

  beforeEach(() => {
    jest.mocked(sessionProxy).mockResolvedValue(NextResponse.next())
    process.env = { ...env, PROFILE_URL: 'http://profile:3001' }
  })

  afterAll(() => {
    process.env = env
  })

  it.each([
    ['/profile', 'http://profile:3001/profile'],
    ['/profile/edit?step=2', 'http://profile:3001/profile/edit?step=2'],
    ['/profile/_next/static/app.js', 'http://profile:3001/profile/_next/static/app.js'],
  ])('routes %s to the profile zone', async (path, expected) => {
    expect(await rewrittenTo(proxy(request(path)))).toBe(expected)
  })

  it('reads the zone url when the request is made, so one image works in every environment', async () => {
    process.env.PROFILE_URL = 'https://profile.example'

    expect(await rewrittenTo(proxy(request('/profile')))).toBe('https://profile.example/profile')
  })

  it('defaults to the local dev port', async () => {
    delete process.env.PROFILE_URL

    expect(await rewrittenTo(proxy(request('/profile')))).toBe('http://localhost:3001/profile')
  })

  it('does not mistake a path that only starts like a zone for it', async () => {
    expect(await rewrittenTo(proxy(request('/profiles')))).toBeNull()
  })

  it('leaves zone sessions to the zone, which refreshes them itself', async () => {
    await proxy(request('/profile'))

    expect(sessionProxy).not.toHaveBeenCalled()
  })

  it('needs a sign in for home', async () => {
    const home = request('/')

    await proxy(home)

    expect(sessionProxy).toHaveBeenCalledWith(home, undefined, { requireSignIn: true })
  })

  it('keeps the session fresh on its other pages, which do not need a sign in', async () => {
    const error = request('/auth/error')

    await proxy(error)

    expect(sessionProxy).toHaveBeenCalledWith(error, undefined, { requireSignIn: false })
  })

  it('runs for everything except static files', () => {
    expect(config.matcher).toEqual(['/((?!_next/static|_next/image|favicon.ico).*)'])
  })
})
