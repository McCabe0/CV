import React from 'react'
import type { JobMatchResult } from '../api'

type JobCardProps = {
  match: JobMatchResult
}

function toneClass(value: number): string {
  if (value >= 75) return 'tone-strong'
  if (value >= 50) return 'tone-good'
  return 'tone-weak'
}

function ringColor(value: number): string {
  if (value >= 75) return '#10b981'
  if (value >= 50) return '#f59e0b'
  return '#ef4444'
}

function getJobLink(match: JobMatchResult): string {
  if (match.job.url) return match.job.url
  if (match.job.source?.startsWith('http')) return match.job.source

  const query = encodeURIComponent(`${match.job.title} ${match.job.company} ${match.job.location} jobs`)
  return `https://www.google.com/search?q=${query}`
}

function Meter({ label, value }: { label: string; value: number }) {
  return (
    <div className="meter-row">
      <div className="meter-head">
        <span>{label}</span>
        <span>{value}%</span>
      </div>
      <div className="meter">
        <div className={`meter-fill ${toneClass(value)}`} style={{ width: `${value}%` }} />
      </div>
    </div>
  )
}

export default function JobCard({ match }: JobCardProps) {
  const { job } = match

  return (
    <article className="job-card grid">
      <div className="job-card-top">
        <div className="grid" style={{ gap: 4 }}>
          <h3>{job.title}</h3>
          <p className="job-company">
            <strong>{job.company}</strong> · {job.location}
          </p>
        </div>
        <div
          className="score-ring"
          style={{ '--val': match.score, '--ring-color': ringColor(match.score) } as React.CSSProperties}
          role="img"
          aria-label={`Match score ${match.score} percent`}
        >
          <span className="score-num">{match.score}</span>
          <span className="score-label">Match</span>
        </div>
      </div>

      <div className="meters">
        <Meter label="Confidence" value={match.confidence} />
        <Meter label="Skill overlap" value={match.skillOverlapPercent} />
      </div>

      {match.reasoning && match.reasoning !== 'Reasoning not requested' ? (
        <p className="job-reasoning">{match.reasoning}</p>
      ) : null}

      {match.requiredSkillsMissing.length > 0 ? (
        <div className="chips">
          <span className="chips-label">Missing:</span>
          {match.requiredSkillsMissing.map((skill) => (
            <span key={skill} className="chip chip-weak">{skill}</span>
          ))}
        </div>
      ) : (
        <div className="chips">
          <span className="chip chip-strong">All required skills covered 🎉</span>
        </div>
      )}

      <a className="link-btn" href={getJobLink(match)} target="_blank" rel="noreferrer">
        View job posting ↗
      </a>
    </article>
  )
}
