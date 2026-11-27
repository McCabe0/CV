import type { Status } from './cvRequest'
import styles from './CvSteps.module.css'

const STEPS = ['Interview', 'Your answers', 'Writing', 'Ready'] as const

// Which step each status is at. A failed request has no place on the way.
const AT: Partial<Record<Status, number>> = {
  REQUESTED: 0,
  AWAITING_ANSWERS: 1,
  WRITING: 2,
  COMPLETED: 3,
}

/** Where a CV request is, as the four steps every CV goes through. */
export function CvSteps({ status }: { status: Status }) {
  const at = AT[status]
  if (at === undefined) return null
  const finished = status === 'COMPLETED'

  return (
    <ol aria-label="Your CV's progress" className={styles.steps}>
      {STEPS.map((name, index) => {
        const done = index < at || (finished && index === at)
        const current = index === at
        return (
          <li
            key={name}
            aria-current={current ? 'step' : undefined}
            className={`${styles.step} ${done ? styles.done : ''} ${current ? styles.current : ''}`}
          >
            <span className={styles.dot} aria-hidden="true">
              {done ? '✓' : index + 1}
            </span>
            <span className={styles.name}>
              {done && <span className="visually-hidden">Done: </span>}
              <span>{name}</span>
            </span>
          </li>
        )
      })}
    </ol>
  )
}
