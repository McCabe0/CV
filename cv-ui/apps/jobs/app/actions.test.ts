import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { askWhyItFits, readWhyItFits } from './actions'

jest.mock('@cv/auth/server', () => ({ requireSession: jest.fn() }))
jest.mock('@cv/api-client', () => ({ apiClient: jest.fn() }))

const GET = jest.fn()
const POST = jest.fn()
const SORRY = 'Sorry, something went wrong. Try again later.'
const GONE = 'This job is no longer listed.'

beforeEach(() => {
  jest.mocked(requireSession).mockResolvedValue({
    user: { username: 'casey', name: 'Casey', roles: ['CANDIDATE'] },
    accessToken: 'access',
    refreshToken: 'refresh',
    expiresAt: 0,
  })
  jest.mocked(apiClient).mockReturnValue({ GET, POST } as unknown as ReturnType<typeof apiClient>)
})

const writing = { jobId: 42, status: 'WRITING' }
const ready = { jobId: 42, status: 'READY', text: 'Your Kotlin fits.' }

describe('askWhyItFits', () => {
  it('asks the api, as the signed in candidate, and gives the summary as it stands', async () => {
    POST.mockResolvedValue({ data: writing, response: { status: 202 } })

    expect(await askWhyItFits(42, {}, new FormData())).toEqual({ summary: writing })

    expect(requireSession).toHaveBeenCalledWith('/jobs')
    expect(apiClient).toHaveBeenCalledWith('access')
    expect(POST).toHaveBeenCalledWith('/jobs/{jobId}/fit', { params: { path: { jobId: 42 } } })
  })

  it('gives one already written at once', async () => {
    POST.mockResolvedValue({ data: ready, response: { status: 200 } })

    expect(await askWhyItFits(42, {}, new FormData())).toEqual({ summary: ready })
  })

  it('says when the job is no longer listed', async () => {
    POST.mockResolvedValue({ data: undefined, error: {}, response: { status: 404 } })

    expect(await askWhyItFits(42, {}, new FormData())).toEqual({ error: GONE })
  })

  it('says sorry when the api fails or cannot be reached', async () => {
    POST.mockResolvedValueOnce({ data: undefined, error: {}, response: { status: 500 } })
    expect(await askWhyItFits(42, {}, new FormData())).toEqual({ error: SORRY })

    POST.mockRejectedValueOnce(new TypeError('fetch failed'))
    expect(await askWhyItFits(42, {}, new FormData())).toEqual({ error: SORRY })
  })
})

describe('readWhyItFits', () => {
  it('reads the summary as it stands now, while the candidate waits', async () => {
    GET.mockResolvedValue({ data: ready, response: { status: 200 } })

    expect(await readWhyItFits(42)).toEqual({ summary: ready })
    expect(GET).toHaveBeenCalledWith('/jobs/{jobId}/fit', { params: { path: { jobId: 42 } } })
  })

  it('says sorry when it cannot be read', async () => {
    GET.mockResolvedValueOnce({ data: undefined, error: {}, response: { status: 500 } })
    expect(await readWhyItFits(42)).toEqual({ error: SORRY })

    GET.mockRejectedValueOnce(new TypeError('fetch failed'))
    expect(await readWhyItFits(42)).toEqual({ error: SORRY })
  })
})
