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
    return (
      <section className="card grid" aria-busy="true">
        <div className="skeleton skeleton-line lg" />
        <div className="skeleton skeleton-line sm" />
        <div className="skeleton skeleton-card" />
        <div className="skeleton skeleton-line" />
        <div className="skeleton skeleton-line" />
      </section>
    )
  }

  if (error) {
    return <section className="card"><p className="error">{error}</p></section>
  }

  if (!cv) {
    return <section className="card"><p className="muted">No CV available yet. Complete Step 1 to generate your CV.</p></section>
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
      <div className="section-head">
        <h2>Step 2 · Tune your CV &amp; search</h2>
        <div className="btn-row">
          <button type="button" onClick={onBackToProfile}>
            Back to profile
          </button>
          <button type="button" onClick={onRegenerate}>
            ↻ Regenerate
          </button>
          <button type="button" onClick={onDownloadPdf}>
            ↓ Download PDF
          </button>
        </div>
      </div>
      <p className="muted">Edit anything below before running job matching.</p>

      <div className="field">
        <label>Headline</label>
        <input value={cv.headline} onChange={(e) => update({ headline: e.target.value })} />
      </div>
      <div className="field">
        <label>Professional summary</label>
        <textarea value={cv.summary} onChange={(e) => update({ summary: e.target.value })} rows={5} />
      </div>

      <div className="grid-2">
        <div className="field">
          <label>Key skills</label>
          <input
            value={cv.keySkills.join(', ')}
            onChange={(e) => update({ keySkills: splitCsv(e.target.value) })}
            placeholder="Key skills"
          />
        </div>
        <div className="field">
          <label>ATS keywords</label>
          <input
            value={cv.atsKeywords.join(', ')}
            onChange={(e) => update({ atsKeywords: splitCsv(e.target.value) })}
            placeholder="ATS keywords"
          />
        </div>
      </div>

      <div className="field">
        <label>Experience bullets (one per line)</label>
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
      </div>

      <div className="field">
        <label>Education &amp; certifications</label>
        <textarea value={cv.educationSection} onChange={(e) => update({ educationSection: e.target.value })} rows={3} />
      </div>

      <form onSubmit={handleSubmit} className="subcard grid">
        <h3>Job search criteria</h3>
        <div className="grid-2">
          <input
            value={jobKeywords}
            onChange={(e) => setJobKeywords(e.target.value)}
            placeholder="Role keywords (comma-separated)"
          />
          <input value={jobLocation} onChange={(e) => setJobLocation(e.target.value)} placeholder="Job location" />
        </div>

        <label className="field">
          <span>Minimum match score: <span className="field-range-value">{minScore}</span></span>
          <input
            type="range"
            min={0}
            max={100}
            step={5}
            value={minScore}
            onChange={(e) => setMinScore(Number(e.target.value))}
          />
        </label>

        <label className="field-check">
          <input
            type="checkbox"
            checked={includeReasoning}
            onChange={(e) => setIncludeReasoning(e.target.checked)}
          />
          Generate AI reasoning now (uses more API calls)
        </label>

        {includeReasoning ? (
          <label className="field">
            <span>Number of jobs with reasoning</span>
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
          {continueLoading ? <><span className="spinner" />Searching &amp; matching…</> : 'Find job matches →'}
        </button>
      </form>
    </section>
  )
}
