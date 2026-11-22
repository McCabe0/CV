import { render, screen, within } from '@testing-library/react'
import { axe } from 'jest-axe'
import { DateField, SelectField, TextArea, TextField } from './fields'

describe('TextField', () => {
  it('is labelled, and named for the form', () => {
    render(<TextField name="firstName" label="First name" defaultValue="Jane" />)

    const input = screen.getByRole('textbox', { name: 'First name' })
    expect(input).toHaveAttribute('name', 'firstName')
    expect(input).toHaveAttribute('id', 'firstName')
    expect(input).toHaveValue('Jane')
    expect(input).not.toHaveAttribute('aria-invalid')
  })

  it('marks an optional field in its label', () => {
    render(<TextField name="email" label="Email address" optional />)

    expect(screen.getByRole('textbox', { name: 'Email address (optional)' })).toBeInTheDocument()
  })

  it('describes the field with its hint', () => {
    render(<TextField name="postcode" label="Postcode" hint="For example, SW1A 2AA" />)

    expect(screen.getByRole('textbox', { name: 'Postcode' })).toHaveAccessibleDescription(
      'For example, SW1A 2AA',
    )
  })

  it('shows its error, described and marked invalid', () => {
    render(
      <TextField
        name="postcode"
        label="Postcode"
        hint="For example, SW1A 2AA"
        error="Enter a postcode"
      />,
    )

    const input = screen.getByRole('textbox', { name: 'Postcode' })
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('For example, SW1A 2AA Error: Enter a postcode')
  })

  it('passes input attributes through', () => {
    render(
      <TextField name="telephone" label="Telephone" type="tel" autoComplete="tel" width="short" />,
    )

    const input = screen.getByRole('textbox', { name: 'Telephone' })
    expect(input).toHaveClass('short')
    expect(input).toHaveAttribute('type', 'tel')
    expect(input).toHaveAttribute('autocomplete', 'tel')
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(
      <TextField
        name="postcode"
        label="Postcode"
        hint="For example, SW1A 2AA"
        error="Enter a postcode"
      />,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('TextArea', () => {
  it('is labelled, described and shows its error', () => {
    render(
      <TextArea
        name="risk"
        label="Risk assessment"
        hint="10 to 500 characters"
        error="Enter a risk assessment"
        defaultValue="Low"
      />,
    )

    const textarea = screen.getByRole('textbox', { name: 'Risk assessment' })
    expect(textarea.tagName).toBe('TEXTAREA')
    expect(textarea).toHaveAttribute('name', 'risk')
    expect(textarea).toHaveValue('Low')
    expect(textarea).toHaveAttribute('aria-invalid', 'true')
    expect(textarea).toHaveAccessibleDescription(
      '10 to 500 characters Error: Enter a risk assessment',
    )
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<TextArea name="risk" label="Risk assessment" optional />)

    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('SelectField', () => {
  const options = [
    { value: 'RETAIL_THEFT', label: 'Retail theft' },
    { value: 'SPEED_AWARENESS', label: 'Speed awareness' },
  ]

  it('offers the options after an empty choice, and keeps the selected one', () => {
    render(
      <SelectField name="course" label="Course" options={options} defaultValue="SPEED_AWARENESS" />,
    )

    const select = screen.getByRole('combobox', { name: 'Course' })
    expect(select).toHaveAttribute('name', 'course')
    expect(
      within(select)
        .getAllByRole('option')
        .map((o) => o.textContent),
    ).toEqual(['Select an option', 'Retail theft', 'Speed awareness'])
    expect(select).toHaveValue('SPEED_AWARENESS')
  })

  it('starts on the empty choice', () => {
    render(<SelectField name="course" label="Course" options={options} />)

    expect(screen.getByRole('combobox', { name: 'Course' })).toHaveValue('')
  })

  it('shows its error, described and marked invalid', () => {
    render(<SelectField name="course" label="Course" options={options} error="Select a course" />)

    const select = screen.getByRole('combobox', { name: 'Course' })
    expect(select).toHaveAttribute('aria-invalid', 'true')
    expect(select).toHaveAccessibleDescription('Error: Select a course')
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(
      <SelectField
        name="course"
        label="Course"
        hint="Choose one"
        options={options}
        error="Select a course"
      />,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('DateField', () => {
  it('is a group of day, month and year, named by its legend', () => {
    render(
      <DateField
        name="offenceDate"
        legend="Date of offence"
        hint="For example, 27 3 2026"
        defaultValue={{ day: '27', month: '3', year: '2026' }}
      />,
    )

    const group = screen.getByRole('group', { name: 'Date of offence' })
    expect(group).toHaveAccessibleDescription('For example, 27 3 2026')
    const day = within(group).getByRole('textbox', { name: 'Day' })
    expect(day).toHaveAttribute('name', 'offenceDate-day')
    expect(day).toHaveAttribute('id', 'offenceDate-day')
    expect(day).toHaveAttribute('inputmode', 'numeric')
    expect(day).toHaveValue('27')
    expect(within(group).getByRole('textbox', { name: 'Month' })).toHaveValue('3')
    expect(within(group).getByRole('textbox', { name: 'Year' })).toHaveValue('2026')
  })

  it('can autocomplete a date of birth', () => {
    render(<DateField name="dateOfBirth" legend="Date of birth" autoComplete="bday" />)

    expect(screen.getByRole('textbox', { name: 'Day' })).toHaveAttribute('autocomplete', 'bday-day')
    expect(screen.getByRole('textbox', { name: 'Month' })).toHaveAttribute(
      'autocomplete',
      'bday-month',
    )
    expect(screen.getByRole('textbox', { name: 'Year' })).toHaveAttribute(
      'autocomplete',
      'bday-year',
    )
  })

  it('shows its error on the group, and marks every part invalid', () => {
    render(
      <DateField
        name="offenceDate"
        legend="Date of offence"
        error="Enter the date of the offence"
      />,
    )

    expect(screen.getByRole('group', { name: 'Date of offence' })).toHaveAccessibleDescription(
      'Error: Enter the date of the offence',
    )
    for (const name of ['Day', 'Month', 'Year']) {
      expect(screen.getByRole('textbox', { name })).toHaveAttribute('aria-invalid', 'true')
    }
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(
      <DateField
        name="offenceDate"
        legend="Date of offence"
        hint="For example, 27 3 2026"
        error="Enter a date"
      />,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
