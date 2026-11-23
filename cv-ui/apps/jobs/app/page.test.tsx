import { render, screen, within } from '@testing-library/react'
import { axe } from 'jest-axe'
import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import Page, { metadata } from './page'
import type { JobMatch } from './jobs'

jest.mock('@cv/auth/server', () => ({ requireSession: jest.fn() }))
jest.mock('@cv/api-client', () => ({ apiClient: jest.fn() }))
jest.mock('./actions', () => ({ askWhyItFits: jest.fn(), readWhyItFits: jest.fn() }))
jest.mock('./WhyItFits', () => ({ WhyItFits: () => <button>Why it fits</button> }))

const GET = jest.fn()

const match = (overrides: Partial<JobMatch> = {}): JobMatch => ({
  id: 42,
  title: 'Backend Engineer',
  company: 'Monzo',
  location: 'London or Remote (UK)',
  workplace: 'REMOTE',
  applyUrl: 'https://monzo.com/careers/42',
  matchedSkills: ['Kotlin', 'Kafka'],
  firstSeenAt: '2026-11-20T06:00:00Z',
  ...overrides,
})

const apiAnswers = (data: JobMatch[] | undefined, status = 200) =>
  GET.mockResolvedValue({ data, response: { status } })

beforeEach(() => {
  jest.mocked(requireSession).mockResolvedValue({
    user: { username: 'casey', name: 'Casey', roles: ['CANDIDATE'] },
    accessToken: 'access',
    refreshToken: 'refresh',
    expiresAt: 0,
  })
  jest.mocked(apiClient).mockReturnValue({ GET } as unknown as ReturnType<typeof apiClient>)
})

const renderPage = async (searchParams: Record<string, string> = {}) =>
  render(await Page({ searchParams: Promise.resolve(searchParams) }))

describe('jobs page', () => {
  it("shows the candidate's matches, best first, each with where and what it asks for", async () => {
    apiAnswers([
      match(),
      match({ id: 43, title: 'Data Engineer', workplace: null, location: null }),
    ])

    await renderPage()

    expect(metadata.title).toBe('Jobs for you — Skill2Career')
    expect(screen.getByRole('heading', { level: 1, name: 'Jobs for you' })).toBeInTheDocument()
    expect(screen.getByText('2 jobs')).toBeInTheDocument()
    const [first, second] = screen.getAllByRole('listitem')
    expect(within(first!).getByRole('heading', { name: 'Backend Engineer' })).toBeInTheDocument()
    expect(first).toHaveTextContent('Monzo · London or Remote (UK) · Remote')
    expect(first).toHaveTextContent('Asks for Kotlin and Kafka')
    expect(second).toHaveTextContent('Monzo')
    expect(second).not.toHaveTextContent('·')
  })

  it("links to apply on the company's own site, in a new tab, saying so", async () => {
    apiAnswers([match()])

    await renderPage()

    const apply = screen.getByRole('link', { name: /Apply on Monzo's site/ })
    expect(apply).toHaveAttribute('href', 'https://monzo.com/careers/42')
    expect(apply).toHaveAttribute('target', '_blank')
    expect(apply).toHaveAttribute('rel', 'noopener noreferrer')
    expect(apply).toHaveTextContent('opens in a new tab')
  })

  it('offers why it fits, and a CV written for it in the CV area', async () => {
    apiAnswers([match()])

    await renderPage()

    expect(screen.getByRole('button', { name: 'Why it fits' })).toBeInTheDocument()
    // The CV area is another zone, so a plain link, which starts the CV there.
    expect(screen.getByRole('link', { name: /Write a CV for this job/ })).toHaveAttribute(
      'href',
      '/cv?job=42',
    )
  })

  it('asks the api for the filter in the url, and fills the form with it', async () => {
    apiAnswers([])

    await renderPage({ remoteOnly: 'on', location: ' UK ' })

    expect(GET).toHaveBeenCalledWith('/jobs/matches', {
      params: { query: { remoteOnly: true, location: 'UK' } },
    })
    expect(screen.getByRole('checkbox', { name: 'Remote only' })).toBeChecked()
    expect(screen.getByRole('textbox', { name: 'Location' })).toHaveValue('UK')
  })

  it('asks for no location when none is given', async () => {
    apiAnswers([])

    await renderPage()

    expect(GET).toHaveBeenCalledWith('/jobs/matches', {
      params: { query: { remoteOnly: false, location: undefined } },
    })
    expect(screen.getByRole('checkbox', { name: 'Remote only' })).not.toBeChecked()
  })

  it('says when nothing matches, and how to find more', async () => {
    apiAnswers([])

    await renderPage({ remoteOnly: 'on' })

    expect(screen.getByText(/No jobs match your skills/)).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('sends a candidate with no profile to make one, as matches come from it', async () => {
    apiAnswers(undefined, 404)

    await renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Save your profile to see jobs that fit it.',
    )
    // The profile is another zone, so a plain link.
    expect(screen.getByRole('link', { name: 'Go to your profile' })).toHaveAttribute(
      'href',
      '/profile',
    )
  })

  it('says sorry when the api fails or cannot be reached', async () => {
    apiAnswers(undefined, 500)
    await renderPage()
    expect(screen.getByRole('alert')).toHaveTextContent('Sorry, your jobs cannot be shown')

    GET.mockRejectedValue(new TypeError('fetch failed'))
    await renderPage()
    expect(screen.getAllByRole('alert').at(-1)).toHaveTextContent(
      'Sorry, your jobs cannot be shown',
    )
  })

  it('has no detectable accessibility violations', async () => {
    apiAnswers([match(), match({ id: 43 })])

    const { container } = await renderPage()

    expect(await axe(container)).toHaveNoViolations()
  })
})
