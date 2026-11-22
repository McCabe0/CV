'use client'

import { useActionState } from 'react'
import { ErrorSummary, TextArea, useErrorTitle, useFormAction } from '@cv/ui'
import type { AnswerState, StartState } from './actions'
import { answerField, type Question } from './cvRequest'
import styles from './forms.module.css'

type StartProps = {
  action: (state: StartState, data: FormData) => Promise<StartState>
  initialState?: StartState
}

export function StartCvForm({ action, initialState = {} }: StartProps) {
  const [state, formAction, pending] = useActionState(action, initialState)

  return (
    <form action={formAction} className={styles.form}>
      {state.needsProfile && (
        <p role="alert" className={styles.problem}>
          Save your profile before asking for a CV.{' '}
          {/* The profile is another zone, so a plain link. */}
          <a href="/profile">Go to your profile</a>
        </p>
      )}
      {state.error && (
        <p role="alert" className={styles.problem}>
          {state.error}
        </p>
      )}
      <button type="submit" disabled={pending}>
        Write my CV
      </button>
    </form>
  )
}

type AnswerProps = {
  questions: Question[]
  action: (state: AnswerState, data: FormData) => Promise<AnswerState>
  initialState?: AnswerState
}

export function AnswerForm({ questions, action, initialState = {} }: AnswerProps) {
  const [state, formAction, pending] = useFormAction(action, initialState)
  useErrorTitle(Boolean(state.error))

  return (
    <form action={formAction} noValidate className={styles.form}>
      <ErrorSummary errors={state.error ? [{ message: state.error }] : []} />
      {questions.map((question) => (
        <TextArea
          key={question.id}
          name={answerField(question)}
          label={question.text}
          hint="Leave it blank to skip."
          defaultValue={state.values?.[answerField(question)]}
          optional
        />
      ))}
      <button type="submit" disabled={pending}>
        Send answers
      </button>
    </form>
  )
}
