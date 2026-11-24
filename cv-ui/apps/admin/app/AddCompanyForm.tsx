'use client'

import { ErrorSummary, SelectField, TextField, useErrorTitle, useFormAction } from '@cv/ui'
import type { AddState } from './actions'
import { BOARDS, type CompanyErrors } from './companies'
import styles from './admin.module.css'

type Props = {
  action: (state: AddState, data: FormData) => Promise<AddState>
  initialState?: AddState
}

const FIELDS: (keyof CompanyErrors)[] = ['name', 'source', 'boardToken']

/**
 * Adds a company to track. The api reads its board first, so a wrong token is refused here, in the
 * api's words. Once added, the form is empty again, ready for the next.
 */
export function AddCompanyForm({ action, initialState = {} }: Props) {
  const [state, formAction, pending] = useFormAction(action, initialState)
  const errors = [
    ...FIELDS.flatMap((field) => {
      const message = state.errors?.[field]
      return message ? [{ href: `#${field}`, message }] : []
    }),
    ...(state.formError ? [{ message: state.formError }] : []),
  ]
  useErrorTitle(errors.length > 0)

  return (
    <section aria-labelledby="add-heading" className={styles.add}>
      <h2 id="add-heading">Add a company</h2>
      {state.added && <p role="status">Added {state.added}. Its jobs will show within a minute.</p>}
      <form action={formAction} noValidate>
        <ErrorSummary errors={errors} />
        <TextField
          name="name"
          label="Company name"
          defaultValue={state.values?.name}
          error={state.errors?.name}
          autoComplete="off"
        />
        <SelectField
          name="source"
          label="Job board"
          hint="The system the company lists its jobs on"
          options={BOARDS}
          defaultValue={state.values?.source}
          error={state.errors?.source}
        />
        <TextField
          name="boardToken"
          label="Board token"
          hint="The company’s name as in the board’s web address, eg stripe in boards.greenhouse.io/stripe"
          defaultValue={state.values?.boardToken}
          error={state.errors?.boardToken}
          autoComplete="off"
          spellCheck={false}
          width="medium"
        />
        <button type="submit" disabled={pending}>
          Add company
        </button>
      </form>
    </section>
  )
}
