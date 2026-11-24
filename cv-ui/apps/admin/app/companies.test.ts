import { boardLabel, crawlSummary, formatWhen, readCompanyForm } from './companies'

const form = (fields: Record<string, string>) => {
  const data = new FormData()
  Object.entries(fields).forEach(([name, value]) => data.set(name, value))
  return data
}

describe('boardLabel', () => {
  it("names each kind of board as its makers do, and the board's own name after it", () => {
    expect(boardLabel('GREENHOUSE', 'monzo')).toBe('Greenhouse: monzo')
    expect(boardLabel('LEVER', 'spotify')).toBe('Lever: spotify')
    expect(boardLabel('ASHBY', 'ramp')).toBe('Ashby: ramp')
    expect(boardLabel('WORKABLE', 'blueground')).toBe('Workable: blueground')
  })
})

describe('formatWhen', () => {
  it('gives the date and time in the UK', () => {
    expect(formatWhen('2026-11-24T06:05:00Z')).toBe('24 Nov 2026, 06:05')
    expect(formatWhen('2026-07-01T06:05:00Z')).toBe('1 Jul 2026, 07:05')
  })
})

describe('crawlSummary', () => {
  const at = '2026-11-24T06:05:00Z'

  it('says what a crawl that worked found, added and closed', () => {
    expect(crawlSummary({ at, outcome: 'WORKED', found: 53, added: 4, closed: 2 })).toEqual({
      status: 'Worked',
      detail: '53 jobs listed, 4 new, 2 closed',
      problem: false,
    })
    expect(crawlSummary({ at, outcome: 'WORKED', found: 1, added: 1, closed: 0 }).detail).toBe(
      '1 job listed, 1 new, 0 closed',
    )
  })

  it('says why a crawl failed, and when it will be tried again', () => {
    expect(
      crawlSummary({ at, outcome: 'FAILED', found: 0, added: 0, closed: 0, error: '503' }),
    ).toEqual({ status: 'Failed, trying again soon', detail: '503', problem: true })
    expect(
      crawlSummary({ at, outcome: 'PARKED', found: 0, added: 0, closed: 0, error: '503' }),
    ).toEqual({ status: 'Failed every try, trying again tomorrow', detail: '503', problem: true })
  })

  it('says a company has not been crawled yet', () => {
    expect(crawlSummary(undefined)).toEqual({
      status: 'Not crawled yet',
      detail: '',
      problem: false,
    })
  })
})

describe('readCompanyForm', () => {
  it('reads a company, trimmed', () => {
    expect(
      readCompanyForm(form({ name: ' Stripe ', source: 'GREENHOUSE', boardToken: ' stripe ' })),
    ).toEqual({
      values: { name: 'Stripe', source: 'GREENHOUSE', boardToken: 'stripe' },
      errors: {},
    })
  })

  it('says what is missing', () => {
    expect(readCompanyForm(form({ name: ' ', source: '', boardToken: '' })).errors).toEqual({
      name: 'Enter the company name',
      source: 'Select the kind of job board',
      boardToken: 'Enter the board token',
    })
  })

  it('refuses a kind of board it cannot read, and a token that is not a plain name', () => {
    const { errors } = readCompanyForm(
      form({ name: 'Stripe', source: 'INDEED', boardToken: 'jobs/stripe' }),
    )

    expect(errors).toEqual({
      source: 'Select the kind of job board',
      boardToken: 'Enter the board token as it is in the board’s web address, eg stripe',
    })
  })

  it('refuses a name or token that is too long', () => {
    const { errors } = readCompanyForm(
      form({ name: 'a'.repeat(101), source: 'LEVER', boardToken: 'b'.repeat(101) }),
    )

    expect(errors).toEqual({
      name: 'Company name must be 100 characters or fewer',
      boardToken: 'Board token must be 100 characters or fewer',
    })
  })
})
