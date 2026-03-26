import React, { useEffect, useMemo, useState } from 'react'
import type { JobMatchResult } from '../api'
import JobCard from './JobCard'

type JobMatchListProps = {
  matches: JobMatchResult[]
  loading?: boolean
  error?: string | null
  onBackToCv: () => void
}

const PAGE_SIZE = 4

export default function JobMatchList({ matches, loading, error, onBackToCv }: JobMatchListProps) {
  const [page, setPage] = useState(1)

  useEffect(() => {
    setPage(1)
  }, [matches])

  const totalPages = Math.max(1, Math.ceil(matches.length / PAGE_SIZE))

  const pageItems = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE
    return matches.slice(start, start + PAGE_SIZE)
  }, [matches, page])

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

      {!loading && !error ? pageItems.map((match) => <JobCard key={`${match.job.id}-${match.job.company}`} match={match} />) : null}

      {!loading && !error && matches.length > PAGE_SIZE ? (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <button type="button" onClick={() => setPage((current) => Math.max(1, current - 1))} disabled={page <= 1}>
            Previous
          </button>
          <p className="muted" style={{ margin: 0 }}>
            Page {page} / {totalPages}
          </p>
          <button type="button" onClick={() => setPage((current) => Math.min(totalPages, current + 1))} disabled={page >= totalPages}>
            Next
          </button>
        </div>
      ) : null}
    </section>
  )
}
