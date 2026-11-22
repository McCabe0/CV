import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ErrorSummary } from './ErrorSummary'
import { useFormAction } from './useFormAction'
import { useErrorTitle } from './useErrorTitle'

type State = {
  values: { name: string }
  errors: { name?: string }
  formError?: string
  error?: string
}

let respond: (state: State) => void
const action = (_: State, data: FormData) =>
  new Promise<State>((resolve) => {
    respond = resolve
    void data
  })

function Form() {
  const [state, formAction, pending] = useFormAction(action, {
    values: { name: 'kept' },
    errors: {},
  })
  const errors = [
    ...(state.formError ? [{ message: state.formError }] : []),
    ...(state.error ? [{ message: state.error }] : []),
    ...(state.errors.name ? [{ href: '#name', message: state.errors.name }] : []),
  ]
  useErrorTitle(errors.length > 0)
  return (
    <form action={formAction}>
      <ErrorSummary errors={errors} />
      <p>value: {state.values.name}</p>
      <p>{pending ? 'sending' : 'ready'}</p>
      <button type="submit">Send</button>
    </form>
  )
}

const failed: State = {
  values: { name: 'kept' },
  errors: { name: 'Enter a name' },
  formError: 'Sorry.',
  error: 'Oops.',
}

beforeEach(() => {
  document.title = 'Form — Skill2Career'
})

describe('useFormAction', () => {
  it("hides the last submission's errors while the next is sent, keeping what was entered", async () => {
    render(<Form />)
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    await act(async () => respond(failed))
    expect(screen.getByRole('region', { name: 'There is a problem' })).toHaveTextContent(
      'Sorry.Oops.Enter a name',
    )
    expect(document.title).toBe('Error: Form — Skill2Career')

    await userEvent.click(screen.getByRole('button', { name: 'Send' }))

    expect(await screen.findByText('sending')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'There is a problem' })).not.toBeInTheDocument()
    expect(document.title).toBe('Form — Skill2Career')
    expect(screen.getByText('value: kept')).toBeInTheDocument()
    // React holds back later updates until every pending action is done, so this one is finished.
    await act(async () => respond(failed))
  })

  it('shows, and focuses, the errors the server sends back', async () => {
    render(<Form />)
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    await act(async () => respond(failed))
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    await screen.findByText('sending')

    await act(async () => respond({ ...failed, formError: undefined, error: undefined }))

    const summary = await screen.findByRole('region', { name: 'There is a problem' })
    expect(summary).toHaveTextContent('Enter a name')
    expect(summary).toHaveFocus()
    expect(document.title).toBe('Error: Form — Skill2Career')
  })

  it('leaves a state without field errors without them', async () => {
    function Simple() {
      const [shown, formAction] = useFormAction(
        async () => new Promise<{ error?: string }>(() => {}),
        { error: 'Before.' },
      )
      return (
        <form action={formAction}>
          <p>{JSON.stringify(shown)}</p>
          <button type="submit">Go</button>
        </form>
      )
    }
    render(<Simple />)
    expect(screen.getByText('{"error":"Before."}')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Go' }))

    // Undefined errors are left out of the json: there is no field errors object.
    expect(await screen.findByText('{}')).toBeInTheDocument()
  })
})
