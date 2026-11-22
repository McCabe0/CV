import { apiClient } from '@cv/api-client'
import { redirectInZone, requireSession } from '@cv/auth/server'
import { answerQuestions, startCv } from './actions'

jest.mock('@cv/auth/server', () => ({
  requireSession: jest.fn(),
  redirectInZone: jest.fn(async () => {
    throw new Error('NEXT_REDIRECT')
  }),
}))
jest.mock('@cv/api-client', () => ({ apiClient: jest.fn() }))

const POST = jest.fn()
const PUT = jest.fn()
const SORRY = 'Sorry, something went wrong. Try again later.'

beforeEach(() => {
  jest.mocked(requireSession).mockResolvedValue({
    user: { username: 'casey', name: 'Casey', roles: ['CANDIDATE'] },
    accessToken: 'access',
    refreshToken: 'refresh',
    expiresAt: 0,
  })
  jest.mocked(apiClient).mockReturnValue({ POST, PUT } as unknown as ReturnType<typeof apiClient>)
})

describe('startCv', () => {
  it('asks for a CV as the signed in candidate, then shows its page', async () => {
    POST.mockResolvedValue({ data: { id: 7 }, response: { status: 202 } })

    await expect(startCv({}, new FormData())).rejects.toThrow('NEXT_REDIRECT')

    expect(requireSession).toHaveBeenCalledWith('/cv')
    expect(apiClient).toHaveBeenCalledWith('access')
    expect(POST).toHaveBeenCalledWith('/cv-requests')
    expect(redirectInZone).toHaveBeenCalledWith('/cv', '/requests/7')
  })

  it('asks for a profile first, as the CV is written only from it', async () => {
    POST.mockResolvedValue({ data: undefined, error: {}, response: { status: 409 } })

    expect(await startCv({}, new FormData())).toEqual({ needsProfile: true })
  })

  it('says sorry when the api fails or cannot be reached', async () => {
    POST.mockResolvedValueOnce({ data: undefined, error: {}, response: { status: 500 } })
    expect(await startCv({}, new FormData())).toEqual({ error: SORRY })

    POST.mockRejectedValueOnce(new TypeError('fetch failed'))
    expect(await startCv({}, new FormData())).toEqual({ error: SORRY })
  })
})

describe('answerQuestions', () => {
  const questions = [
    { id: 1, text: 'By how much?' },
    { id: 2, text: 'Which team?' },
  ]
  const answered = () => {
    const data = new FormData()
    data.set('answer-1', 'By 40%')
    return data
  }

  it('sends the answers, then shows the request being written', async () => {
    PUT.mockResolvedValue({ data: { id: 7 }, response: { status: 202 } })

    await expect(answerQuestions(7, questions, {}, answered())).rejects.toThrow('NEXT_REDIRECT')

    expect(PUT).toHaveBeenCalledWith('/cv-requests/{id}/answers', {
      params: { path: { id: 7 } },
      body: {
        answers: [
          { questionId: 1, text: 'By 40%' },
          { questionId: 2, text: null },
        ],
      },
    })
    expect(redirectInZone).toHaveBeenCalledWith('/cv', '/requests/7')
  })

  it('shows the request as it is when it has already moved on, eg answered in another tab', async () => {
    PUT.mockResolvedValue({ data: undefined, error: {}, response: { status: 409 } })

    await expect(answerQuestions(7, questions, {}, answered())).rejects.toThrow('NEXT_REDIRECT')

    expect(redirectInZone).toHaveBeenCalledWith('/cv', '/requests/7')
  })

  it('says sorry, keeping what was typed, when the api fails', async () => {
    PUT.mockResolvedValue({ data: undefined, error: {}, response: { status: 500 } })

    expect(await answerQuestions(7, questions, {}, answered())).toEqual({
      error: SORRY,
      values: { 'answer-1': 'By 40%', 'answer-2': '' },
    })
  })

  it('says sorry when the api cannot be reached', async () => {
    PUT.mockRejectedValue(new TypeError('fetch failed'))

    expect((await answerQuestions(7, questions, {}, answered())).error).toBe(SORRY)
  })

  it('refuses an answer over the limit, on its box, without calling the api, keeping what was typed', async () => {
    const data = answered()
    data.set('answer-2', 'a'.repeat(2001))

    const state = await answerQuestions(7, questions, {}, data)

    expect(PUT).not.toHaveBeenCalled()
    expect(state.errors).toEqual({
      'answer-2': 'Keep this answer to 2,000 characters. It has 2,001.',
    })
    expect(state.values!['answer-2']).toHaveLength(2001)
  })

  it('says to check the answers, not to try later, when the api refuses them', async () => {
    PUT.mockResolvedValue({ data: undefined, error: {}, response: { status: 400 } })

    expect((await answerQuestions(7, questions, {}, answered())).error).toBe(
      'Some answers were not accepted. Check them and try again.',
    )
  })
})
