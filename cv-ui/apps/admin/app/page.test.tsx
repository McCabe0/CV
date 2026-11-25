import { render, screen, within } from '@testing-library/react'
import { axe } from 'jest-axe'
import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import Page, { dynamic, metadata } from './page'
import type { Company } from './companies'

jest.mock('@cv/auth/server', () => ({ requireSession: jest.fn() }))
jest.mock('@cv/api-client', () => ({ apiClient: jest.fn() }))
jest.mock('./actions', () => ({
  addCompany: jest.fn(),
  crawlNow: jest.fn(),
  stopTracking: jest.fn(),
  trackAgain: jest.fn(),
}))
jest.mock('./AddCompanyForm', () => ({ AddCompanyForm: () => <form aria-label="Add a company" /> }))
jest.mock('./CompanyActions', () => ({
  CompanyActions: ({ crawl, trackAgain }: { crawl?: unknown; trackAgain?: unknown }) => (
    <p>{crawl ? 'tracked buttons' : trackAgain ? 'track again button' : 'no buttons'}</p>
  ),
}))

const GET = jest.fn()

const company = (overrides: Partial<Company> = {}): Company => ({
  id: 1,
  name: 'Monzo',
  source: 'GREENHOUSE',
  boardToken: 'monzo',
  tracked: true,
  liveJobs: 53,
  lastCrawl: { at: '2026-11-24T06:05:00Z', outcome: 'WORKED', found: 53, added: 4, closed: 2 },
  ...overrides,
})

const apiAnswers = (data: Company[] | undefined, status = 200) =>
  GET.mockResolvedValue({ data, response: { status } })

beforeEach(() => {
  jest.mocked(requireSession).mockResolvedValue({
    user: { username: 'alex', name: 'Alex', roles: ['ADMIN'] },
    accessToken: 'access',
    refreshToken: 'refresh',
    expiresAt: 0,
  })
  jest.mocked(apiClient).mockReturnValue({ GET } as unknown as ReturnType<typeof apiClient>)
})

const renderPage = async () => render(await Page())

describe('admin page', () => {
  it('lists each company with its board, live jobs and last crawl, read fresh each time', async () => {
    apiAnswers([company()])

    await renderPage()

    expect(metadata.title).toBe('Companies — Skill2Career')
    expect(dynamic).toBe('force-dynamic')
    expect(screen.getByRole('heading', { level: 1, name: 'Companies' })).toBeInTheDocument()
    expect(GET).toHaveBeenCalledWith('/admin/companies')
    const monzo = screen.getByRole('listitem')
    expect(within(monzo).getByRole('heading', { level: 3, name: 'Monzo' })).toBeInTheDocument()
    expect(monzo).toHaveTextContent('Greenhouse: monzo')
    expect(monzo).toHaveTextContent('53 live jobs')
    expect(monzo).toHaveTextContent('Last crawl: Worked, 24 Nov 2026, 06:05')
    expect(monzo).toHaveTextContent('53 jobs listed, 4 new, 2 closed')
    expect(monzo).toHaveTextContent('tracked buttons')
  })

  it('shows a failed crawl as a problem, with why', async () => {
    apiAnswers([
      company({
        lastCrawl: {
          at: '2026-11-24T06:05:00Z',
          outcome: 'PARKED',
          found: 0,
          added: 0,
          closed: 0,
          error: "board 'monzo' could not be read: 503",
        },
      }),
    ])

    await renderPage()

    const problem = screen.getByText(/Last crawl: Failed every try/)
    expect(problem).toHaveTextContent("board 'monzo' could not be read: 503")
  })

  it('says a new company has not been crawled yet', async () => {
    apiAnswers([company({ liveJobs: 1, lastCrawl: null })])

    await renderPage()

    expect(screen.getByRole('listitem')).toHaveTextContent('1 live job')
    expect(screen.getByRole('listitem')).toHaveTextContent('Last crawl: Not crawled yet')
  })

  it('shows a company no longer tracked, since when, offering to track it again', async () => {
    apiAnswers([company({ tracked: false, untrackedAt: '2026-11-24T06:05:00Z', liveJobs: 0 })])

    await renderPage()

    const monzo = screen.getByRole('listitem')
    expect(monzo).toHaveTextContent('Not tracked since 24 Nov 2026, 06:05')
    expect(monzo).toHaveTextContent('track again button')
  })

  it('says when there are no companies yet', async () => {
    apiAnswers([])

    await renderPage()

    expect(screen.getByText('No companies are tracked yet.')).toBeInTheDocument()
    expect(screen.getByRole('form', { name: 'Add a company' })).toBeInTheDocument()
  })

  it('says sorry when the companies cannot be read', async () => {
    apiAnswers(undefined, 500)

    await renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Sorry, the companies cannot be shown at the moment. Try again later.',
    )
  })

  it('says sorry when the api cannot be reached', async () => {
    GET.mockRejectedValue(new Error('down'))

    await renderPage()

    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('has no detectable accessibility violations', async () => {
    apiAnswers([company(), company({ id: 2, name: 'Ramp', tracked: false, lastCrawl: null })])

    const { container } = await renderPage()

    expect(await axe(container)).toHaveNoViolations()
  })
})
