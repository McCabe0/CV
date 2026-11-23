'use client'

import { useActionState, useEffect, useState } from 'react'
import type { FitState } from './actions'
import styles from './jobs.module.css'

/** How often to check back while the summary is written. It takes a few seconds. */
export const CHECK_EVERY_MS = 2000

/** About a minute of checking, then the candidate is asked to check again themselves. */
export const MAX_CHECKS = 30

type Props = {
  /** Asks for the summary: a form action, so the button works without javascript. */
  ask: (state: FitState, data: FormData) => Promise<FitState>
  /** Reads the summary as it stands, while it is written. */
  read: () => Promise<FitState>
  initialState?: FitState
}

/** What the checks found since the last ask. A newer ask makes it out of date. */
type Checked = { after: FitState; state: FitState; checks: number }

/** Why a job fits the candidate: asked for on a click, then checked back on until it is written. */
export function WhyItFits({ ask, read, initialState = {} }: Props) {
  const [asked, formAction, pending] = useActionState(ask, initialState)
  const [checked, setChecked] = useState<Checked | null>(null)
  const { state, checks } = checked?.after === asked ? checked : { state: asked, checks: 0 }
  const status = state.summary?.status
  const checking = status === 'WRITING' && checks < MAX_CHECKS

  useEffect(() => {
    if (!checking) return
    const timer = setTimeout(async () => {
      setChecked({ after: asked, state: await read(), checks: checks + 1 })
    }, CHECK_EVERY_MS)
    return () => clearTimeout(timer)
  }, [checking, checks, asked, read])

  if (status === 'READY') return <p className={styles.fit}>{state.summary!.text}</p>

  if (status === 'WRITING') {
    return checking ? (
      <p role="status" className={styles.fit}>
        Working out why this job fits you…
      </p>
    ) : (
      <div className={styles.fit}>
        <p role="status">This is taking longer than usual. Check again in a minute.</p>
        <button type="button" onClick={() => setChecked({ after: asked, state, checks: 0 })}>
          Check again
        </button>
      </div>
    )
  }

  const problem = status === 'FAILED' ? state.summary!.failure : state.error
  return (
    <form action={formAction} className={styles.fit}>
      {problem && <p role="alert">{problem}</p>}
      <button type="submit" disabled={pending} className={styles.secondary}>
        {problem ? 'Ask again' : 'Why it fits'}
      </button>
    </form>
  )
}
