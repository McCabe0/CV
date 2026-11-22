/** @jest-environment @stryker-mutator/jest-runner/jest-env/node */
import { getSession } from '@cv/auth/server'
import { GET } from './route'

jest.mock('@cv/auth/server', () => ({ getSession: jest.fn() }))

const signedIn = () =>
  jest.mocked(getSession).mockResolvedValue({
    user: { username: 'casey', name: 'Casey', roles: ['CANDIDATE'] },
    accessToken: 'access',
    refreshToken: 'refresh',
    expiresAt: 0,
  })

const call = (id = '7') =>
  GET(new Request('http://localhost/cv/requests/7/events'), { params: Promise.resolve({ id }) })

describe('the progress stream, through this zone', () => {
  const env = process.env
  let fetchSpy: jest.SpyInstance

  beforeEach(() => {
    process.env = { ...env, API_URL: 'http://api:8080/api' }
    fetchSpy = jest.spyOn(global, 'fetch')
  })

  afterEach(() => {
    fetchSpy.mockRestore()
    process.env = env
  })

  it("passes the api's stream on, with the candidate's token, which never reaches the browser", async () => {
    signedIn()
    fetchSpy.mockResolvedValue(
      new Response('event:progress\ndata:{}\n\n', {
        headers: { 'content-type': 'text/event-stream' },
      }),
    )

    const response = await call()

    const [url, init] = fetchSpy.mock.calls[0]!
    expect(url).toBe('http://api:8080/api/cv-requests/7/events')
    expect(init.headers).toEqual({ Authorization: 'Bearer access', Accept: 'text/event-stream' })
    expect(response.status).toBe(200)
    expect(response.headers.get('content-type')).toBe('text/event-stream')
    expect(response.headers.get('cache-control')).toBe('no-cache, no-transform')
    expect(await response.text()).toBe('event:progress\ndata:{}\n\n')
  })

  it('refuses a signed out browser', async () => {
    jest.mocked(getSession).mockResolvedValue(null)

    expect((await call()).status).toBe(401)
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it("passes on the api's refusal, eg another candidate's request", async () => {
    signedIn()
    fetchSpy.mockResolvedValue(new Response(null, { status: 404 }))

    expect((await call()).status).toBe(404)
  })

  it('is not found for an id that is not a number', async () => {
    signedIn()

    expect((await call('../profile')).status).toBe(404)
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
