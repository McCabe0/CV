import { apiClient } from './client'

const respondWith = (body: unknown, status = 200) =>
  jest.fn<Promise<Response>, [Request]>(async () => Response.json(body, { status }))

describe('apiClient', () => {
  const env = process.env

  beforeEach(() => {
    process.env = { ...env, API_URL: 'https://api.example/api' }
  })

  afterAll(() => {
    process.env = env
  })

  it("calls the api with the signed in user's access token", async () => {
    const fetch = respondWith({})

    await apiClient('access-token', { fetch }).GET('/profile')

    const request = fetch.mock.calls[0]![0]
    expect(request.url).toBe('https://api.example/api/profile')
    expect(request.headers.get('Authorization')).toBe('Bearer access-token')
  })

  it('sends the typed body as json', async () => {
    const fetch = respondWith({})

    const body = {
      name: 'Casey',
      skills: ['Kotlin'],
      experience: 'Lots',
      education: 'Some',
      projects: [],
      certifications: [],
      languages: [],
      workHistory: [],
    }

    await apiClient('access-token', { fetch }).PUT('/profile', { body })

    const request = fetch.mock.calls[0]![0]
    expect(request.method).toBe('PUT')
    expect(await request.json()).toEqual(body)
  })

  it("returns the api's problem as the error", async () => {
    const fetch = respondWith({ status: 404, detail: 'You have no profile yet.' }, 404)

    const { data, error, response } = await apiClient('access-token', { fetch }).GET('/profile')

    expect(data).toBeUndefined()
    expect(response.status).toBe(404)
    expect(error).toEqual({ status: 404, detail: 'You have no profile yet.' })
  })

  it('needs to know where the api is', () => {
    delete process.env.API_URL

    expect(() => apiClient('access-token')).toThrow('Missing API_URL')
  })
})
