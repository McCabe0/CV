import React, { FormEvent, useState } from 'react'
import type { CvResponse } from '../api'

type JobCriteria = {
  roleKeywords: string[]
  location?: string
  includeReasoning: boolean
  reasoningLimit: number
  minScore: number
}

type CvPreviewProps = {
  cv: CvResponse | null
  loading?: boolean
  error?: string | null
  onUpdateCv: (cv: CvResponse) => void
  onContinue: (criteria: JobCriteria) => void
  continueLoading?: boolean
  onBackToProfile: () => void
  onRegenerate: () => void
  onDownloadPdf: () => void
}

function splitCsv(value: string): string[] {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

export default function CvPreview({
  cv,
  loading,
  error,
  onUpdateCv,
  onContinue,
  continueLoading,
  onBackToProfile,
  onRegenerate,
  onDownloadPdf,
}: CvPreviewProps) {
  const [jobKeywords, setJobKeywords] = useState('')
  const [jobLocation, setJobLocation] = useState('')
  const [includeReasoning, setIncludeReasoning] = useState(false)
  const [reasoningLimit, setReasoningLimit] = useState(3)
  const [minScore, setMinScore] = useState(0)

  if (loading) {
    return <p className="muted">Loading CV preview...</p>
  }

  if (error) {
    return <p className="error">{error}</p>
  }

  if (!cv) {
    return <p className="muted">No CV available yet. Complete Step 1 to generate your CV.</p>
  }

  const update = (patch: Partial<CvResponse>) => {
    onUpdateCv({ ...cv, ...patch })
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onContinue({
      roleKeywords: splitCsv(jobKeywords),
      location: jobLocation.trim() || undefined,
      includeReasoning,
      reasoningLimit,
      minScore,
    })
  }

  return (
    <section className="card grid">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0 }}>Step 2: Tune your CV and search criteria</h2>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button type="button" onClick={onBackToProfile}>
            Back to profile
          </button>
          <button type="button" onClick={onRegenerate}>
            Regenerate CV
          </button>
          <button type="button" onClick={onDownloadPdf}>
            Download PDF
          </button>
        </div>
      </div>
      <p className="muted" style={{ margin: 0 }}>Edit anything below before running job matching.</p>

      <input value={cv.headline} onChange={(e) => update({ headline: e.target.value })} />
      <textarea value={cv.summary} onChange={(e) => update({ summary: e.target.value })} rows={5} />

      <div className="grid-2">
        <input
          value={cv.keySkills.join(', ')}
          onChange={(e) => update({ keySkills: splitCsv(e.target.value) })}
          placeholder="Key skills"
        />
        <input
          value={cv.atsKeywords.join(', ')}
          onChange={(e) => update({ atsKeywords: splitCsv(e.target.value) })}
          placeholder="ATS keywords"
        />
      </div>

      <textarea
        value={cv.experienceBullets.join('\n')}
        onChange={(e) =>
          update({
            experienceBullets: e.target.value
              .split('\n')
              .map((line) => line.trim())
              .filter(Boolean),
          })
        }
        rows={5}
      />

      <textarea value={cv.educationSection} onChange={(e) => update({ educationSection: e.target.value })} rows={3} />

      <form onSubmit={handleSubmit} className="card grid" style={{ background: '#f8fafc' }}>
        <h3 style={{ margin: 0 }}>Job Search Criteria</h3>
        <div className="grid-2">
          <input
            value={jobKeywords}
            onChange={(e) => setJobKeywords(e.target.value)}
            placeholder="Role keywords (comma-separated)"
          />
          <input value={jobLocation} onChange={(e) => setJobLocation(e.target.value)} placeholder="Job location" />
        </div>

        <label style={{ display: 'grid', gap: 6 }}>
          Minimum match score: {minScore}
          <input
            type="range"
            min={0}
            max={100}
            step={5}
            value={minScore}
            onChange={(e) => setMinScore(Number(e.target.value))}
          />
        </label>

        <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <input
            type="checkbox"
            checked={includeReasoning}
            onChange={(e) => setIncludeReasoning(e.target.checked)}
            style={{ width: 16, height: 16 }}
          />
          Generate AI reasoning now (uses more API calls)
        </label>

        {includeReasoning ? (
          <label style={{ display: 'grid', gap: 6 }}>
            Number of jobs with reasoning
            <input
              type="number"
              min={1}
              max={10}
              value={reasoningLimit}
              onChange={(e) => setReasoningLimit(Math.max(1, Math.min(10, Number(e.target.value) || 3)))}
            />
          </label>
        ) : null}

        <button type="submit" disabled={continueLoading}>
          {continueLoading ? 'Searching and matching...' : 'Find Job Matches'}
        </button>
      </form>
    </section>
  )
}
