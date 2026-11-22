import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { CharacterCount } from './CharacterCount'

const renderCount = (props: Partial<Parameters<typeof CharacterCount>[0]> = {}) =>
  render(<CharacterCount name="answer-1" label="By how much?" maxLength={2000} {...props} />)

describe('CharacterCount', () => {
  it('says how many characters are left, from the start', () => {
    renderCount()

    expect(screen.getByText('You have 2,000 characters remaining')).toBeInTheDocument()
  })

  it('counts what was entered before, eg kept after an error', () => {
    renderCount({ defaultValue: 'a'.repeat(1990) })

    expect(screen.getByText('You have 10 characters remaining')).toBeInTheDocument()
  })

  it('counts as the user types', async () => {
    renderCount({ maxLength: 10 })

    await userEvent.type(screen.getByRole('textbox', { name: 'By how much?' }), 'By 40%')

    expect(screen.getByText('You have 4 characters remaining')).toBeInTheDocument()
  })

  it('says one character, not one characters', async () => {
    renderCount({ maxLength: 3 })

    await userEvent.type(screen.getByRole('textbox'), 'ab')

    expect(screen.getByText('You have 1 character remaining')).toBeInTheDocument()
  })

  it('says how many too many, without stopping the typing, so a long paste can be trimmed', async () => {
    renderCount({ maxLength: 5 })
    const box = screen.getByRole('textbox')

    await userEvent.type(box, 'By 40 per cent')

    expect(box).toHaveValue('By 40 per cent')
    expect(box).not.toHaveAttribute('maxlength')
    expect(screen.getByText('You have 9 characters too many')).toBeInTheDocument()
  })

  it('reads the count, the hint and any error with the box', () => {
    renderCount({ hint: 'Leave it blank to skip.', error: 'Keep this answer shorter' })

    expect(screen.getByRole('textbox')).toHaveAccessibleDescription(
      expect.stringMatching(
        /Leave it blank to skip\..*Keep this answer shorter.*2,000 characters remaining/,
      ),
    )
    expect(screen.getByRole('textbox')).toHaveAttribute('aria-invalid', 'true')
  })

  it('is marked optional when it is', () => {
    renderCount({ optional: true })

    expect(screen.getByRole('textbox', { name: 'By how much? (optional)' })).toBeInTheDocument()
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = renderCount({ hint: 'Up to 2,000 characters.', error: 'Too long' })

    expect(await axe(container)).toHaveNoViolations()
  })
})
