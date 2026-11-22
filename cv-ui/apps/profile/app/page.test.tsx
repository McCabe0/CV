import { render, screen } from '@testing-library/react'
import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import Page from './page'

jest.mock('@cv/auth/server', () => ({ requireSession: jest.fn() }))
jest.mock('@cv/api-client', () => ({ apiClient: jest.fn() }))
jest.mock('./actions', () => ({ saveProfile: jest.fn() }))

const GET = jest.fn()

beforeEach(() => {
  jest.mocked(requireSession).mockResolvedValue({
    user: { username: 'casey', name: 'Casey Candidate', roles: ['CANDIDATE'] },
    accessToken: 'access',
    refreshToken: 'refresh',
    expiresAt: 0,
  })
  jest.mocked(apiClient).mockReturnValue({ GET } as unknown as ReturnType<typeof apiClient>)
})

const renderPage = async () => render(await Page())

describe('profile page', () => {
  it('fills the form from the saved profile', async () => {
    GET.mockResolvedValue({
      data: {
        id: 1,
        name: 'Casey',
        skills: ['Kotlin'],
        experience: 'Lots',
        education: 'Some',
        projects: [],
        certifications: [],
        languages: [],
        workHistory: [],
      },
      response: { status: 200 },
    })

    await renderPage()

    expect(requireSession).toHaveBeenCalledWith('/profile')
    expect(apiClient).toHaveBeenCalledWith('access')
    expect(GET).toHaveBeenCalledWith('/profile')
    expect(screen.getByRole('heading', { level: 1, name: 'Your profile' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Skills' })).toHaveValue('Kotlin')
  })

  it('starts a first profile with the name on the account', async () => {
    GET.mockResolvedValue({ data: undefined, error: { status: 404 }, response: { status: 404 } })

    await renderPage()

    expect(screen.getByText(/Tell us about yourself/)).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Full name' })).toHaveValue('Casey Candidate')
    expect(screen.getByRole('textbox', { name: 'Skills' })).toHaveValue('')
  })

  it('says sorry, without a form, when the profile cannot be loaded', async () => {
    GET.mockResolvedValue({ data: undefined, error: {}, response: { status: 500 } })

    await renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Sorry, your profile cannot be shown at the moment.',
    )
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('says sorry when the api cannot be reached', async () => {
    GET.mockRejectedValue(new TypeError('fetch failed'))

    await renderPage()

    expect(screen.getByRole('alert')).toBeInTheDocument()
  })
})
