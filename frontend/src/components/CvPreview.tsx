import { FormEvent, useState } from 'react'
import type { CvResponse } from '../api'

type JobCriteria = {
  roleKeywords: string[]
  location?: string
}

type CvPreviewProps = {
  cv: CvResponse | null
  loading?: boolean
  error?: string | null
  onUpdateCv: (cv: CvResponse) => void
  onContinue: (criteria: JobCriteria) => void
  continueLoading?: boolean
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
}: CvPreviewProps) {
  const [jobKeywords, setJobKeywords] = useState('')
  const [jobLocation, setJobLocation] = useState('')

  if (loading) {
    return <p>Loading CV preview...</p>
  }

  if (error) {
    return <p style={{ color: 'crimson' }}>{error}</p>
  }

  if (!cv) {
    return <p>No CV available yet. Complete Step 1 to generate your CV.</p>
  }

  const update = (patch: Partial<CvResponse>) => {
    onUpdateCv({ ...cv, ...patch })
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onContinue({
      roleKeywords: splitCsv(jobKeywords),
      location: jobLocation.trim() || undefined,
    })
  }

  return (
    <section style={{ display: 'grid', gap: 12 }}>
      <h2>Step 2: CV preview/editor</h2>
      <input value={cv.headline} onChange={(e) => update({ headline: e.target.value })} />
      <textarea value={cv.summary} onChange={(e) => update({ summary: e.target.value })} rows={5} />
      <input
        value={cv.keySkills.join(', ')}
        onChange={(e) => update({ keySkills: splitCsv(e.target.value) })}
        placeholder="Key skills"
      />
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
      <textarea
        value={cv.educationSection}
        onChange={(e) => update({ educationSection: e.target.value })}
        rows={3}
      />
      <input
        value={cv.atsKeywords.join(', ')}
        onChange={(e) => update({ atsKeywords: splitCsv(e.target.value) })}
        placeholder="ATS keywords"
      />

      <form onSubmit={handleSubmit} style={{ display: 'grid', gap: 8 }}>
        <h3>Job Search Criteria</h3>
        <input
          value={jobKeywords}
          onChange={(e) => setJobKeywords(e.target.value)}
          placeholder="Role keywords (comma-separated)"
        />
        <input value={jobLocation} onChange={(e) => setJobLocation(e.target.value)} placeholder="Job location" />
        <button type="submit" disabled={continueLoading}>
          {continueLoading ? 'Searching and matching...' : 'Find Job Matches'}
        </button>
      </form>
    </section>
  )
}
