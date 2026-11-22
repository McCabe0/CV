'use client'

import { useActionState } from 'react'
import { CharacterCount, ErrorSummary, useErrorTitle, useFormAction, type FormError } from '@cv/ui'
import type { AnswerState, StartState } from './actions'
import { ANSWER_MAX_LENGTH, answerField, type Question } from './cvRequest'
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
  const errors = state.errors ?? {}
  const summary: FormError[] = [
    ...(state.error ? [{ message: state.error }] : []),
    ...Object.entries(errors).map(([field, message]) => ({ href: `#${field}`, message })),
  ]
  useErrorTitle(summary.length > 0)

  return (
    <form action={formAction} noValidate className={styles.form}>
      <ErrorSummary errors={summary} />
      {questions.map((question) => (
        <CharacterCount
          key={question.id}
          name={answerField(question)}
          label={question.text}
          hint={`Leave it blank to skip. Up to ${ANSWER_MAX_LENGTH.toLocaleString('en-GB')} characters.`}
          maxLength={ANSWER_MAX_LENGTH}
          defaultValue={state.values?.[answerField(question)]}
          error={errors[answerField(question)]}
          optional
        />
      ))}
      <button type="submit" disabled={pending}>
        Send answers
      </button>
    </form>
  )
}
