'use client'

import { useActionState, type ReactNode } from 'react'
import type { RowState } from './actions'
import styles from './admin.module.css'

type Action = (state: RowState, data: FormData) => Promise<RowState>

/** One button, as a form so it works without javascript, and what it did or why it could not. */
function ActionButton({
  action,
  secondary,
  children,
}: {
  action: Action
  secondary?: boolean
  children: ReactNode
}) {
  const [state, formAction, pending] = useActionState(action, {})
  return (
    <form action={formAction} className={styles.action}>
      <button type="submit" disabled={pending} className={secondary ? styles.secondary : undefined}>
        {children}
      </button>
      {state.message && <p role="status">{state.message}</p>}
      {state.error && <p role="alert">{state.error}</p>}
    </form>
  )
}

type Props = {
  name: string
  /** A tracked company's buttons. */
  crawl?: Action
  stop?: Action
  /** An untracked company's button. */
  trackAgain?: Action
}

/** What an admin can do with one company, each button naming it for screen readers. */
export function CompanyActions({ name, crawl, stop, trackAgain }: Props) {
  const company = <span className="visually-hidden">{name} </span>
  return (
    <div className={styles.actions}>
      {crawl && <ActionButton action={crawl}>Crawl {company}now</ActionButton>}
      {stop && (
        <ActionButton action={stop} secondary>
          Stop tracking<span className="visually-hidden"> {name}</span>
        </ActionButton>
      )}
      {trackAgain && <ActionButton action={trackAgain}>Track {company}again</ActionButton>}
    </div>
  )
}
