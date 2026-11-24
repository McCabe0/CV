import { revalidatePath } from 'next/cache'
import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { addCompany, crawlNow, stopTracking, trackAgain } from './actions'
import type { Company } from './companies'

jest.mock('next/cache', () => ({ revalidatePath: jest.fn() }))
jest.mock('@cv/auth/server', () => ({ requireSession: jest.fn() }))
jest.mock('@cv/api-client', () => ({ apiClient: jest.fn() }))

const POST = jest.fn()
const DELETE = jest.fn()
const SORRY = 'Sorry, something went wrong. Try again later.'

beforeEach(() => {
  jest.mocked(requireSession).mockResolvedValue({
    user: { username: 'alex', name: 'Alex', roles: ['ADMIN'] },
    accessToken: 'access',
    refreshToken: 'refresh',
    expiresAt: 0,
  })
  jest
    .mocked(apiClient)
    .mockReturnValue({ POST, DELETE } as unknown as ReturnType<typeof apiClient>)
})

const form = (fields: Record<string, string>) => {
  const data = new FormData()
  Object.entries(fields).forEach(([name, value]) => data.set(name, value))
  return data
}

const stripe = { name: 'Stripe', source: 'GREENHOUSE', boardToken: 'stripe' }
const created = { data: { id: 9 }, response: { status: 201 } }
const refused = (status: number, detail: string) => ({
  data: undefined,
  error: { detail },
  response: { status },
})

const monzo: Company = {
  id: 1,
  name: 'Monzo',
  source: 'GREENHOUSE',
  boardToken: 'monzo',
  tracked: false,
  untrackedAt: '2026-11-24T06:00:00Z',
  liveJobs: 0,
}

describe('addCompany', () => {
  it('adds the company, as the signed in admin, and shows the list afresh', async () => {
    POST.mockResolvedValue(created)

    expect(await addCompany({}, form({ ...stripe, name: ' Stripe ' }))).toEqual({ added: 'Stripe' })

    expect(requireSession).toHaveBeenCalledWith('/admin')
    expect(apiClient).toHaveBeenCalledWith('access')
    expect(POST).toHaveBeenCalledWith('/admin/companies', { body: stripe })
    expect(revalidatePath).toHaveBeenCalledWith('/')
  })

  it('sends nothing until the form is right, keeping what was entered', async () => {
    const state = await addCompany({}, form({ name: 'Stripe', source: '', boardToken: '' }))

    expect(state).toEqual({
      values: { name: 'Stripe', source: '', boardToken: '' },
      errors: { source: 'Select the kind of job board', boardToken: 'Enter the board token' },
    })
    expect(POST).not.toHaveBeenCalled()
  })

  it.each([
    [422, "There is no GREENHOUSE board called 'stripe'. Check the board token."],
    [502, 'The board could not be read just now. Try again in a minute.'],
    [409, 'This board is already tracked.'],
  ])('says why the api refused it (%i), in its own words', async (status, detail) => {
    POST.mockResolvedValue(refused(status, detail))

    expect(await addCompany({}, form(stripe))).toEqual({ values: stripe, formError: detail })
    expect(revalidatePath).not.toHaveBeenCalled()
  })

  it('says sorry when the api fails or cannot be reached', async () => {
    POST.mockResolvedValueOnce(refused(500, 'boom')).mockRejectedValueOnce(new Error('down'))

    expect(await addCompany({}, form(stripe))).toEqual({ values: stripe, formError: SORRY })
    expect(await addCompany({}, form(stripe))).toEqual({ values: stripe, formError: SORRY })
  })
})

describe('stopTracking', () => {
  it('stops tracking the company, and shows the list afresh', async () => {
    DELETE.mockResolvedValue({ data: monzo, response: { status: 200 } })

    expect(await stopTracking(1, 'Monzo', {}, new FormData())).toEqual({
      message: 'Stopped tracking Monzo. Its jobs are closed.',
    })
    expect(DELETE).toHaveBeenCalledWith('/admin/companies/{id}', { params: { path: { id: 1 } } })
    expect(revalidatePath).toHaveBeenCalledWith('/')
  })

  it('says why it could not', async () => {
    DELETE.mockResolvedValueOnce(refused(404, 'No such company.')).mockRejectedValueOnce(
      new Error('down'),
    )

    expect(await stopTracking(1, 'Monzo', {}, new FormData())).toEqual({
      error: 'No such company.',
    })
    expect(await stopTracking(1, 'Monzo', {}, new FormData())).toEqual({ error: SORRY })
  })
})

describe('crawlNow', () => {
  it('queues the crawl, saying when to look', async () => {
    POST.mockResolvedValue({ data: undefined, response: { status: 202 } })

    expect(await crawlNow(1, 'Monzo', {}, new FormData())).toEqual({
      message: 'Crawling Monzo now. Refresh the page in a minute to see how it went.',
    })
    expect(POST).toHaveBeenCalledWith('/admin/companies/{id}/crawl', {
      params: { path: { id: 1 } },
    })
  })

  it('says why it could not', async () => {
    POST.mockResolvedValue(refused(409, 'This company is no longer tracked.'))

    expect(await crawlNow(1, 'Monzo', {}, new FormData())).toEqual({
      error: 'This company is no longer tracked.',
    })
  })
})

describe('trackAgain', () => {
  it('tracks an untracked company again, under its name, and shows the list afresh', async () => {
    POST.mockResolvedValue(created)

    expect(await trackAgain(monzo, {}, new FormData())).toEqual({
      message: 'Tracking Monzo again. Its jobs will be back within a minute.',
    })
    expect(POST).toHaveBeenCalledWith('/admin/companies', {
      body: { name: 'Monzo', source: 'GREENHOUSE', boardToken: 'monzo' },
    })
    expect(revalidatePath).toHaveBeenCalledWith('/')
  })

  it('says why it could not, eg the board has gone', async () => {
    POST.mockResolvedValue(refused(422, "There is no GREENHOUSE board called 'monzo'."))

    expect(await trackAgain(monzo, {}, new FormData())).toEqual({
      error: "There is no GREENHOUSE board called 'monzo'.",
    })
  })
})
