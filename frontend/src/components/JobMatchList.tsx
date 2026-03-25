import type { JobMatchResult } from '../api'
import JobCard from './JobCard'

type JobMatchListProps = {
  matches: JobMatchResult[]
  loading?: boolean
  error?: string | null
  onBackToCv: () => void
}

export default function JobMatchList({ matches, loading, error, onBackToCv }: JobMatchListProps) {
  return (
    <section style={{ display: 'grid', gap: 12 }}>
      <h2>Step 3: Job match results</h2>
      <button onClick={onBackToCv} type="button" style={{ width: 'fit-content' }}>
        Back to CV editor
      </button>

      {loading ? <p>Loading job matches...</p> : null}
      {error ? <p style={{ color: 'crimson' }}>{error}</p> : null}
      {!loading && !error && matches.length === 0 ? <p>No job matches found. Try broader criteria.</p> : null}

      {!loading && !error
        ? matches.map((match) => <JobCard key={`${match.job.id}-${match.job.company}`} match={match} />)
        : null}
    </section>
  )
}
