import React from 'react'
import type { JobMatchResult } from '../api'

type JobCardProps = {
  match: JobMatchResult
}

function toneClass(value: number): string {
  if (value >= 75) return 'chip-strong'
  if (value >= 50) return 'chip-good'
  return 'chip-weak'
}

function getJobLink(match: JobMatchResult): string {
  if (match.job.url) return match.job.url
  if (match.job.source?.startsWith('http')) return match.job.source

  const query = encodeURIComponent(`${match.job.title} ${match.job.company} ${match.job.location} jobs`)
  return `https://www.google.com/search?q=${query}`
}

export default function JobCard({ match }: JobCardProps) {
  const { job } = match

  return (
    <article className="job-card grid">
      <h3>{job.title}</h3>
      <p>
        <strong>{job.company}</strong> — {job.location}
      </p>

      <div className="chips">
        <span className={`chip ${toneClass(match.score)}`}>Score: {match.score}%</span>
        <span className={`chip ${toneClass(match.confidence)}`}>Confidence: {match.confidence}%</span>
        <span className={`chip ${toneClass(match.skillOverlapPercent)}`}>Overlap: {match.skillOverlapPercent}%</span>
      </div>

      <p>{match.reasoning}</p>

      {match.requiredSkillsMissing.length > 0 ? (
        <p className="muted">Missing skills: {match.requiredSkillsMissing.join(', ')}</p>
      ) : (
        <p className="muted">Missing skills: none 🎉</p>
      )}

      <a className="link-btn" href={getJobLink(match)} target="_blank" rel="noreferrer">
        View job posting ↗
      </a>
    </article>
  )
}
