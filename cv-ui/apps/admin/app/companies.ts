import type { components } from '@cv/api-client'

export type Company = components['schemas']['CompanyResponse']
export type Crawl = components['schemas']['CrawlResponse']
export type Source = Company['source']

/** The kinds of job board the api can read, as their makers name them. */
export const BOARDS: { value: Source; label: string }[] = [
  { value: 'GREENHOUSE', label: 'Greenhouse' },
  { value: 'LEVER', label: 'Lever' },
  { value: 'ASHBY', label: 'Ashby' },
  { value: 'WORKABLE', label: 'Workable' },
]

/** The longest name and token the api takes, in characters. */
export const MAX_LENGTH = 100

/** A board's name, as in its web address, eg boards.greenhouse.io/stripe. */
const TOKEN = /^[A-Za-z0-9][A-Za-z0-9._-]*$/

export const boardLabel = (source: Source, boardToken: string) =>
  `${BOARDS.find(({ value }) => value === source)?.label ?? source}: ${boardToken}`

/** When something happened, in the UK, eg 24 Nov 2026, 06:05. */
export const formatWhen = (iso: string) =>
  new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Europe/London',
  }).format(new Date(iso))

const jobs = (count: number) => (count === 1 ? '1 job' : `${count} jobs`)

/** A crawl in plain words: how it went, what it found or why it failed, and whether it is a problem. */
export function crawlSummary(crawl: Crawl | null | undefined) {
  if (!crawl) return { status: 'Not crawled yet', detail: '', problem: false }
  switch (crawl.outcome) {
    case 'WORKED':
      return {
        status: 'Worked',
        detail: `${jobs(crawl.found)} listed, ${crawl.added} new, ${crawl.closed} closed`,
        problem: false,
      }
    case 'FAILED':
      return { status: 'Failed, trying again soon', detail: crawl.error ?? '', problem: true }
    default:
      return {
        status: 'Failed every try, trying again tomorrow',
        detail: crawl.error ?? '',
        problem: true,
      }
  }
}

export type CompanyValues = { name: string; source: string; boardToken: string }
export type CompanyErrors = Partial<Record<keyof CompanyValues, string>>

const text = (data: FormData, name: string) => String(data.get(name) ?? '').trim()

/** The add form's company, trimmed, and what is wrong with it, by field, in gov.uk's words. */
export function readCompanyForm(data: FormData): { values: CompanyValues; errors: CompanyErrors } {
  const values = {
    name: text(data, 'name'),
    source: text(data, 'source'),
    boardToken: text(data, 'boardToken'),
  }
  const errors: CompanyErrors = {}

  if (!values.name) errors.name = 'Enter the company name'
  else if (values.name.length > MAX_LENGTH)
    errors.name = `Company name must be ${MAX_LENGTH} characters or fewer`

  if (!BOARDS.some(({ value }) => value === values.source))
    errors.source = 'Select the kind of job board'

  if (!values.boardToken) errors.boardToken = 'Enter the board token'
  else if (values.boardToken.length > MAX_LENGTH)
    errors.boardToken = `Board token must be ${MAX_LENGTH} characters or fewer`
  else if (!TOKEN.test(values.boardToken))
    errors.boardToken = 'Enter the board token as it is in the board’s web address, eg stripe'

  return { values, errors }
}
