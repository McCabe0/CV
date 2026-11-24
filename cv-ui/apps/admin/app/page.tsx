import type { Metadata } from 'next'
import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { addCompany, crawlNow, stopTracking, trackAgain } from './actions'
import { AddCompanyForm } from './AddCompanyForm'
import { BASE_PATH } from './basePath'
import { CompanyActions } from './CompanyActions'
import { boardLabel, crawlSummary, formatWhen, type Company } from './companies'
import styles from './admin.module.css'

// The layout's title template only applies to nested pages, so the zone's root gives its full title.
export const metadata: Metadata = { title: 'Companies — Skill2Career' }

/** Crawls change the list at any time, so it is read afresh on every visit. */
export const dynamic = 'force-dynamic'

/** The companies whose job boards are crawled, how each crawl went, and adding, stopping and crawling them. */
export default async function Page() {
  const { accessToken } = await requireSession(BASE_PATH)
  const companies = await load(accessToken)

  return (
    <>
      <h1>Companies</h1>
      <p>
        The companies whose own job boards are crawled each day for jobs. Adding one reads its board
        first, then crawls it straight away.
      </p>
      <AddCompanyForm action={addCompany} />
      <h2>Tracked companies</h2>
      {companies === undefined ? (
        <p role="alert">Sorry, the companies cannot be shown at the moment. Try again later.</p>
      ) : companies.length === 0 ? (
        <p>No companies are tracked yet.</p>
      ) : (
        <ul className={styles.companies}>
          {companies.map((company) => (
            <CompanyCard key={company.id} company={company} />
          ))}
        </ul>
      )}
    </>
  )
}

function CompanyCard({ company }: { company: Company }) {
  const { id, name, tracked, untrackedAt, liveJobs, lastCrawl } = company
  const crawl = crawlSummary(lastCrawl)
  const when = lastCrawl ? `, ${formatWhen(lastCrawl.at)}` : ''
  return (
    <li className={tracked ? styles.company : `${styles.company} ${styles.untracked}`}>
      <h3>{name}</h3>
      <p className={styles.meta}>
        {boardLabel(company.source, company.boardToken)} ·{' '}
        {liveJobs === 1 ? '1 live job' : `${liveJobs} live jobs`}
      </p>
      {!tracked && untrackedAt && (
        <p className={styles.meta}>Not tracked since {formatWhen(untrackedAt)}</p>
      )}
      <p className={crawl.problem ? styles.problem : styles.meta}>
        Last crawl: {crawl.status}
        {when}
        {crawl.detail && (
          <>
            <br />
            {crawl.detail}
          </>
        )}
      </p>
      {tracked ? (
        <CompanyActions
          name={name}
          crawl={crawlNow.bind(null, id, name)}
          stop={stopTracking.bind(null, id, name)}
        />
      ) : (
        <CompanyActions name={name} trackAgain={trackAgain.bind(null, company)} />
      )}
    </li>
  )
}

async function load(accessToken: string): Promise<Company[] | undefined> {
  try {
    const { data } = await apiClient(accessToken).GET('/admin/companies')
    return data
  } catch {
    return undefined
  }
}
