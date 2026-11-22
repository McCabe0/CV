import type { Round } from './cvRequest'
import styles from './Rounds.module.css'

const KINDS: Record<Round['issues'][number]['kind'], string> = {
  INVENTED_FACT: 'Not in your profile',
  NO_IMPACT: 'Impact',
  MISSING_KEYWORD: 'Keyword',
  GENERIC_SUMMARY: 'Summary',
  OTHER: 'Other',
}

/** Each draft and the critic's review of it: the agents at work. */
export function Rounds({ rounds }: { rounds: Round[] }) {
  if (rounds.length === 0) return null

  return (
    <section aria-labelledby="rounds-heading" className={styles.rounds}>
      <h2 id="rounds-heading">The writer and the critic</h2>
      <ol className={styles.list}>
        {rounds.map((round) => (
          <li key={round.number} className={styles.round}>
            <strong>
              Draft {round.number}: {round.score} out of 100{round.approved ? ', approved' : ''}
            </strong>
            {round.issues.map((issue) => (
              <p key={`${issue.kind}${issue.detail}`} className={styles.issue}>
                {KINDS[issue.kind]}: {issue.detail}
              </p>
            ))}
          </li>
        ))}
      </ol>
    </section>
  )
}
