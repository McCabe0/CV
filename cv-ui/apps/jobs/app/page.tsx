import type { Metadata } from 'next'
import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { askWhyItFits, readWhyItFits } from './actions'
import { BASE_PATH } from './basePath'
import {
  LOCATION_MAX_LENGTH,
  readFilter,
  skillsLine,
  workplaceLabel,
  type Filter,
  type JobMatch,
} from './jobs'
import styles from './jobs.module.css'
import { WhyItFits } from './WhyItFits'

// The layout's title template only applies to nested pages, so the zone's root gives its full title.
export const metadata: Metadata = { title: 'Jobs for you — Skill2Career' }

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> }

/** Live jobs that ask for the candidate's skills, from the companies' own boards, best first. */
export default async function Page({ searchParams }: Props) {
  const filter = readFilter(await searchParams)
  const { accessToken } = await requireSession(BASE_PATH)
  const loaded = await load(accessToken, filter)

  return (
    <>
      <h1>Jobs for you</h1>
      <p>
        Live jobs from companies&apos; own job boards that ask for your skills, the most skills
        first. Each links to the company&apos;s own site to apply.
      </p>
      <FilterForm filter={filter} />
      {loaded === 'no-profile' ? (
        <p role="alert" className={styles.problem}>
          Save your profile to see jobs that fit it.{' '}
          {/* The profile is another zone, so a plain link. */}
          <a href="/profile">Go to your profile</a>
        </p>
      ) : loaded === 'failed' ? (
        <p role="alert" className={styles.problem}>
          Sorry, your jobs cannot be shown at the moment. Try again later.
        </p>
      ) : (
        <Matches matches={loaded} />
      )}
    </>
  )
}

/** A plain get form, so it works without javascript and a search can be shared as a link. */
function FilterForm({ filter }: { filter: Filter }) {
  return (
    <form method="get" className={styles.filter} role="search" aria-label="Filter jobs">
      <div>
        <label htmlFor="location">Location</label>
        <input
          id="location"
          name="location"
          type="text"
          defaultValue={filter.location}
          maxLength={LOCATION_MAX_LENGTH}
          aria-describedby="location-hint"
        />
        <p id="location-hint" className={styles.count}>
          For example London, or UK
        </p>
      </div>
      <div className={styles.checkbox}>
        <input
          id="remoteOnly"
          name="remoteOnly"
          type="checkbox"
          defaultChecked={filter.remoteOnly}
        />
        <label htmlFor="remoteOnly">Remote only</label>
      </div>
      <button type="submit">Show jobs</button>
    </form>
  )
}

function Matches({ matches }: { matches: JobMatch[] }) {
  if (matches.length === 0) {
    return (
      <p>
        No jobs match your skills here yet. Try another location, or without remote only. Adding
        skills to your profile finds more.
      </p>
    )
  }
  return (
    <>
      <p className={styles.count}>{matches.length === 1 ? '1 job' : `${matches.length} jobs`}</p>
      <ul className={styles.jobs}>
        {matches.map((match) => (
          <Job key={match.id} match={match} />
        ))}
      </ul>
    </>
  )
}

function Job({ match }: { match: JobMatch }) {
  const where = [match.company, match.location, workplaceLabel(match.workplace)].filter(Boolean)
  return (
    <li className={styles.job}>
      <h2>{match.title}</h2>
      <p className={styles.meta}>{where.join(' · ')}</p>
      <p className={styles.meta}>{skillsLine(match.matchedSkills)}</p>
      <WhyItFits
        ask={askWhyItFits.bind(null, match.id)}
        read={readWhyItFits.bind(null, match.id)}
      />
      <div className={styles.actions}>
        <a href={match.applyUrl} target="_blank" rel="noopener noreferrer">
          Apply on {match.company}&apos;s site
          <span className="visually-hidden"> (opens in a new tab)</span>
        </a>
        {/* The CV area is another zone, so a plain link, which starts the CV there. */}
        <a href={`/cv?job=${match.id}`}>
          Write a CV for this job<span className="visually-hidden">: {match.title}</span>
        </a>
      </div>
    </li>
  )
}

async function load(accessToken: string, filter: Filter) {
  try {
    const { data, response } = await apiClient(accessToken).GET('/jobs/matches', {
      params: { query: { remoteOnly: filter.remoteOnly, location: filter.location || undefined } },
    })
    if (data) return data
    return response.status === 404 ? 'no-profile' : 'failed'
  } catch {
    return 'failed'
  }
}
