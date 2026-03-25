import React from 'react'
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
    <section className="card grid">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
        <h2 style={{ margin: 0 }}>Step 3: Job match results</h2>
        <button onClick={onBackToCv} type="button">
          Back to CV editor
        </button>
      </div>

      {loading ? <p className="muted">Loading job matches...</p> : null}
      {error ? <p className="error">{error}</p> : null}
      {!loading && !error && matches.length === 0 ? <p className="muted">No matches yet. Try broader keywords or refresh recommendations.</p> : null}

      {!loading && !error ? matches.map((match) => <JobCard key={`${match.job.id}-${match.job.company}`} match={match} />) : null}
    </section>
  )
}
