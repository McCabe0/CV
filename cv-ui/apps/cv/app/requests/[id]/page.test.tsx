import { render, screen } from '@testing-library/react'
import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { notFound } from 'next/navigation'
import { cvRequest, draft, round } from '../../fixtures'
import Page from './page'

jest.mock('@cv/auth/server', () => ({ requireSession: jest.fn() }))
jest.mock('@cv/api-client', () => ({ apiClient: jest.fn() }))
jest.mock('../../actions', () => ({ startCv: jest.fn(), answerQuestions: jest.fn() }))
jest.mock('next/navigation', () => ({
  notFound: jest.fn(() => {
    throw new Error('NEXT_NOT_FOUND')
  }),
  useRouter: () => ({ refresh: jest.fn() }),
}))
jest.mock('../../CvProgress', () => ({
  CvProgress: ({ initial }: { initial: { status: string } }) => <p>Following {initial.status}</p>,
}))

const GET = jest.fn()
const profile = {
  id: 1,
  name: 'Casey Candidate',
  skills: [],
  experience: '',
  education: '',
  projects: [],
  certifications: [],
  languages: [],
  workHistory: [],
  email: 'casey@example.com',
}

/** The api: this request, and the candidate's profile. */
const apiHas = (request: object | null, status = 200) =>
  GET.mockImplementation(async (path: string) =>
    path === '/profile'
      ? { data: profile, response: { status: 200 } }
      : { data: request ?? undefined, response: { status } },
  )

beforeEach(() => {
  jest.mocked(requireSession).mockResolvedValue({
    user: { username: 'casey', name: 'Casey', roles: ['CANDIDATE'] },
    accessToken: 'access',
    refreshToken: 'refresh',
    expiresAt: 0,
  })
  jest.mocked(apiClient).mockReturnValue({ GET } as unknown as ReturnType<typeof apiClient>)
})

const renderPage = async () => render(await Page({ params: Promise.resolve({ id: '7' }) }))

describe('a CV request page', () => {
  it("reads the signed in candidate's request", async () => {
    apiHas(cvRequest())

    await renderPage()

    expect(requireSession).toHaveBeenCalledWith('/cv/requests/7')
    expect(GET).toHaveBeenCalledWith('/cv-requests/{id}', { params: { path: { id: 7 } } })
  })

  it("asks the interviewer's questions while it waits for answers", async () => {
    apiHas(cvRequest({ status: 'AWAITING_ANSWERS', questions: [{ id: 1, text: 'By how much?' }] }))

    await renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'A few questions' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'By how much? (optional)' })).toBeInTheDocument()
  })

  it.each(['REQUESTED', 'WRITING'] as const)('follows the agents at work: %s', async (status) => {
    apiHas(cvRequest({ status }))

    await renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Writing your CV' })).toBeInTheDocument()
    expect(screen.getByText(`Following ${status}`)).toBeInTheDocument()
  })

  it('shows the finished CV, headed with the profile, and how it got there', async () => {
    apiHas(
      cvRequest({ status: 'COMPLETED', cv: draft, rounds: [round(1, 70), round(2, 91, true)] }),
    )

    await renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Your CV' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Casey Candidate' })).toBeInTheDocument()
    expect(screen.getByText(draft.summary)).toBeInTheDocument()
    expect(screen.getByText('Draft 2: 91 out of 100, approved')).toBeInTheDocument()
  })

  it('says why it failed, and offers to try again', async () => {
    apiHas(cvRequest({ status: 'FAILED', failure: 'The writer gave no usable answer' }))

    await renderPage()

    expect(
      screen.getByRole('heading', { level: 1, name: 'Your CV could not be written' }),
    ).toBeInTheDocument()
    expect(screen.getByText('The writer gave no usable answer')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Write my CV' })).toBeInTheDocument()
  })

  it('is not found when it is not the candidate’s, or does not exist', async () => {
    apiHas(null, 404)

    await expect(renderPage()).rejects.toThrow('NEXT_NOT_FOUND')
    expect(notFound).toHaveBeenCalled()
  })

  it('is not found for an id that is not a number', async () => {
    await expect(Page({ params: Promise.resolve({ id: 'abc' }) })).rejects.toThrow('NEXT_NOT_FOUND')
  })

  it('says sorry when the api fails', async () => {
    apiHas(null, 500)

    await renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Sorry, this CV cannot be shown at the moment.',
    )
  })
})
