import { render, screen, within } from '@testing-library/react'
import { axe } from 'jest-axe'
import { Radios } from './fields'

const options = [
  { value: 'NOT_ELIGIBLE', label: 'Offence not eligible' },
  { value: 'NO_ADMISSION', label: 'Candidate failed to admit offence' },
]

describe('Radios', () => {
  it('is a group of choices, named by its legend', () => {
    render(
      <Radios
        name="reason"
        legend="Reason for rejection"
        options={options}
        defaultValue="NO_ADMISSION"
      />,
    )

    const group = screen.getByRole('group', { name: 'Reason for rejection' })
    const first = within(group).getByRole('radio', { name: 'Offence not eligible' })
    expect(first).toHaveAttribute('name', 'reason')
    expect(first).toHaveAttribute('value', 'NOT_ELIGIBLE')
    expect(first).toHaveAttribute('id', 'reason')
    expect(first).not.toBeChecked()
    expect(
      within(group).getByRole('radio', { name: 'Candidate failed to admit offence' }),
    ).toBeChecked()
  })

  it('can say more about each choice, described on its radio', () => {
    render(
      <Radios
        name="sessionId"
        legend="Session"
        options={[
          { value: 'a', label: 'Monday', hint: 'Westminster. 3 places left.' },
          { value: 'b', label: 'Tuesday' },
        ]}
      />,
    )

    expect(screen.getByRole('radio', { name: 'Monday' })).toHaveAccessibleDescription(
      'Westminster. 3 places left.',
    )
    expect(screen.getByRole('radio', { name: 'Tuesday' })).not.toHaveAttribute('aria-describedby')
  })

  it('shows its hint and error on the group, where screen readers announce them', () => {
    render(
      <Radios
        name="reason"
        legend="Reason for rejection"
        hint="Choose one"
        options={options}
        error="Select a reason"
      />,
    )

    expect(screen.getByRole('group', { name: 'Reason for rejection' })).toHaveAccessibleDescription(
      'Choose one Error: Select a reason',
    )
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(
      <Radios
        name="reason"
        legend="Reason for rejection"
        options={options}
        error="Select a reason"
      />,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
