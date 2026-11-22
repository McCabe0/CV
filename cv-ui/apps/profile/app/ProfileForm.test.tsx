import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { ProfileForm } from './ProfileForm'
import { emptyValues, type ProfileFormState } from './profileValues'

const initial: ProfileFormState = { values: { ...emptyValues, name: 'Casey' }, errors: {} }

const renderForm = (state = initial, action = jest.fn(async () => state)) =>
  render(<ProfileForm initialState={state} action={action} />)

describe('ProfileForm', () => {
  beforeEach(() => {
    document.title = 'Your profile — Skill2Career'
  })

  it('is in sections', () => {
    renderForm()

    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
      'About you',
      'Skills and experience',
      'More about you',
      'Contact details',
    ])
  })

  it('asks for each field, with the optional ones marked', () => {
    renderForm()

    for (const name of [
      'Full name',
      'Role you want (optional)',
      'Years of experience (optional)',
      'Location (optional)',
      'Right to work (optional)',
      'Skills',
      'Experience',
      'Education',
      'Projects (optional)',
      'Certifications (optional)',
      'Languages (optional)',
      'Email address (optional)',
      'Phone number (optional)',
      'LinkedIn (optional)',
      'Portfolio or website (optional)',
    ]) {
      expect(screen.getByRole('textbox', { name })).toBeInTheDocument()
    }
  })

  it('says the limit of the long boxes, and counts the characters left as the user types', async () => {
    renderForm()

    expect(screen.getByText('Up to 5,000 characters.', { exact: false })).toBeInTheDocument()
    await userEvent.type(screen.getByRole('textbox', { name: 'Experience' }), 'Five years.')
    expect(screen.getByText('You have 4,989 characters remaining')).toBeInTheDocument()
    expect(screen.getByText('You have 2,000 characters remaining')).toBeInTheDocument()
  })

  it('starts with what was entered', () => {
    renderForm()

    expect(screen.getByRole('textbox', { name: 'Full name' })).toHaveValue('Casey')
  })

  it('sends the form to the action', async () => {
    const action = jest.fn(async () => initial)
    renderForm(initial, action)

    await userEvent.type(screen.getByRole('textbox', { name: 'Skills' }), 'Kotlin')
    await userEvent.click(screen.getByRole('button', { name: 'Save profile' }))

    const data = (action.mock.calls[0] as unknown as [ProfileFormState, FormData])[1]
    expect(data.get('skills')).toBe('Kotlin')
  })

  it('lists the problems, linked to their fields, and marks the title', () => {
    renderForm({ ...initial, errors: { skills: 'Enter at least one skill' } })

    expect(screen.getByRole('link', { name: 'Enter at least one skill' })).toHaveAttribute(
      'href',
      '#skills',
    )
    expect(screen.getByRole('textbox', { name: 'Skills' })).toHaveAccessibleDescription(
      expect.stringContaining('Enter at least one skill'),
    )
    expect(document.title).toBe('Error: Your profile — Skill2Career')
  })

  it('shows a problem with the whole form', () => {
    renderForm({
      ...initial,
      formError: 'Sorry, your profile could not be saved. Try again later.',
    })

    expect(
      screen.getByText('Sorry, your profile could not be saved. Try again later.'),
    ).toBeInTheDocument()
  })

  it('says when the profile was saved, and offers the next step, a CV', () => {
    renderForm({ ...initial, saved: true })

    expect(screen.getByRole('status')).toHaveTextContent('Your profile has been saved.')
    // The cv zone is another zone, so a plain link.
    expect(screen.getByRole('link', { name: 'Write your CV' })).toHaveAttribute('href', '/cv')
  })

  it('has no detectable accessibility violations, with errors or without', async () => {
    const { container, rerender } = renderForm()
    expect(await axe(container)).toHaveNoViolations()

    rerender(
      <ProfileForm
        initialState={{ ...initial, errors: { name: 'Enter your name' } }}
        action={jest.fn()}
      />,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
