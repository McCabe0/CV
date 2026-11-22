'use client'

import { useActionState } from 'react'

/** The errors a form's action can send back: by field, about the whole form, or one. */
type WithErrors = { errors?: object; formError?: string; error?: string }

/** The state without its errors, keeping what was entered. */
const withoutErrors = <State extends WithErrors>(state: State): State => ({
  ...state,
  ...(state.errors && { errors: {} }),
  formError: undefined,
  error: undefined,
})

/**
 * React's useActionState for a form, hiding the last submission's errors while the next is sent, so the error
 * summary, the field messages and the page title's "Error: " do not say a corrected form is still wrong. The
 * server's answer brings back any errors, and the summary takes focus again. Without javascript the browser
 * posts the form and loads the answer, so nothing old is shown.
 */
export function useFormAction<State extends WithErrors>(
  action: (state: State, data: FormData) => Promise<State>,
  initialState: State,
) {
  // React types the state as awaited, which a plain object state already is.
  const [state, formAction, pending] = useActionState(
    action as (state: Awaited<State>, data: FormData) => Promise<State>,
    initialState as Awaited<State>,
  )
  return [pending ? withoutErrors<State>(state) : (state as State), formAction, pending] as const
}
