import type { JobMatchResult } from '../api'

type JobCardProps = {
  match: JobMatchResult
}

export default function JobCard({ match }: JobCardProps) {
  const { job } = match

  return (
    <article style={{ border: '1px solid #ddd', borderRadius: 8, padding: 12, display: 'grid', gap: 6 }}>
      <h3 style={{ margin: 0 }}>{job.title}</h3>
      <p style={{ margin: 0 }}>
        <strong>{job.company}</strong> — {job.location}
      </p>
      <p style={{ margin: 0 }}>Score: {match.score}% | Confidence: {match.confidence}%</p>
      <p style={{ margin: 0 }}>Skill overlap: {match.skillOverlapPercent}%</p>
      {match.requiredSkillsMissing.length > 0 ? (
        <p style={{ margin: 0 }}>Missing skills: {match.requiredSkillsMissing.join(', ')}</p>
      ) : (
        <p style={{ margin: 0 }}>Missing skills: none</p>
      )}
      <p style={{ margin: 0 }}>{match.reasoning}</p>
    </article>
  )
}
