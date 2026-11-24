import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { AddCompanyForm } from './AddCompanyForm'
import type { AddState } from './actions'

/** An add action that answers with [state], keeping what it was sent. */
const answering = (state: AddState) =>
  jest.fn(async (_previous: AddState, _data: FormData) => state)

describe('AddCompanyForm', () => {
  it("asks for the company's name, its kind of board, and the board's token, each explained", () => {
    render(<AddCompanyForm action={jest.fn()} />)

    expect(screen.getByRole('textbox', { name: 'Company name' })).toBeInTheDocument()
    const board = screen.getByRole('combobox', { name: 'Job board' })
    expect(
      Array.from(board.querySelectorAll('option')).map((option) => option.textContent),
    ).toEqual(['Select an option', 'Greenhouse', 'Lever', 'Ashby', 'Workable'])
    expect(screen.getByRole('textbox', { name: 'Board token' })).toHaveAccessibleDescription(
      /as in the board’s web address/,
    )
  })

  it('sends what was entered, and says the company was added', async () => {
    const action = answering({ added: 'Stripe' })
    render(<AddCompanyForm action={action} />)

    await userEvent.type(screen.getByRole('textbox', { name: 'Company name' }), 'Stripe')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Job board' }), 'GREENHOUSE')
    await userEvent.type(screen.getByRole('textbox', { name: 'Board token' }), 'stripe')
    await userEvent.click(screen.getByRole('button', { name: 'Add company' }))

    const sent = action.mock.calls[0]![1] as FormData
    expect(Object.fromEntries(sent)).toEqual({
      name: 'Stripe',
      source: 'GREENHOUSE',
      boardToken: 'stripe',
    })
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Added Stripe. Its jobs will show within a minute.',
    )
    expect(screen.getByRole('textbox', { name: 'Company name' })).toHaveValue('')
    expect(screen.getByRole('combobox', { name: 'Job board' })).toHaveValue('')
  })

  it('keeps every answer, the job board too, when the api refuses the company', async () => {
    const values = { name: 'Acme', source: 'LEVER', boardToken: 'acme' }
    render(<AddCompanyForm action={answering({ values, formError: 'There is no LEVER board.' })} />)

    await userEvent.type(screen.getByRole('textbox', { name: 'Company name' }), 'Acme')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Job board' }), 'LEVER')
    await userEvent.type(screen.getByRole('textbox', { name: 'Board token' }), 'acme')
    await userEvent.click(screen.getByRole('button', { name: 'Add company' }))

    expect(await screen.findByText('There is no LEVER board.')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Company name' })).toHaveValue('Acme')
    expect(screen.getByRole('combobox', { name: 'Job board' })).toHaveValue('LEVER')
    expect(screen.getByRole('textbox', { name: 'Board token' })).toHaveValue('acme')
  })

  it('shows what is wrong with each field, keeping what was entered', async () => {
    render(
      <AddCompanyForm
        action={jest.fn()}
        initialState={{
          values: { name: 'Stripe', source: '', boardToken: '' },
          errors: { source: 'Select the kind of job board', boardToken: 'Enter the board token' },
        }}
      />,
    )

    expect(screen.getByRole('heading', { name: 'There is a problem' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Select the kind of job board' })).toHaveAttribute(
      'href',
      '#source',
    )
    expect(screen.getByRole('textbox', { name: 'Company name' })).toHaveValue('Stripe')
    expect(screen.getByRole('textbox', { name: 'Board token' })).toHaveAccessibleDescription(
      /Enter the board token/,
    )
  })

  it("shows why the api refused the company, eg its board doesn't exist", () => {
    render(
      <AddCompanyForm
        action={jest.fn()}
        initialState={{
          values: { name: 'Stripe', source: 'GREENHOUSE', boardToken: 'strip' },
          formError: "There is no GREENHOUSE board called 'strip'. Check the board token.",
        }}
      />,
    )

    expect(
      screen.getByRole('heading', { name: 'There is a problem' }).parentElement,
    ).toHaveTextContent("There is no GREENHOUSE board called 'strip'. Check the board token.")
    expect(screen.getByRole('combobox', { name: 'Job board' })).toHaveValue('GREENHOUSE')
  })

  it('has no detectable accessibility violations, with errors too', async () => {
    const { container } = render(
      <AddCompanyForm
        action={jest.fn()}
        initialState={{
          values: { name: '', source: '', boardToken: '' },
          errors: { name: 'Enter the company name' },
        }}
      />,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
