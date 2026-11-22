import { fireEvent, render, screen } from '@testing-library/react'
import { axe } from 'jest-axe'
import { ErrorSummary } from './ErrorSummary'

const errors = [
  { href: '#firstName', message: 'Enter a first name' },
  { href: '#offenceDate-day', message: 'Enter the date of the offence' },
]

describe('ErrorSummary', () => {
  it('lists each error, linked to its field', () => {
    render(<ErrorSummary errors={errors} />)

    expect(screen.getByRole('heading', { name: 'There is a problem' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Enter a first name' })).toHaveAttribute(
      'href',
      '#firstName',
    )
    expect(screen.getByRole('link', { name: 'Enter the date of the offence' })).toHaveAttribute(
      'href',
      '#offenceDate-day',
    )
  })

  it('takes focus, so screen reader and keyboard users hear about the errors first', () => {
    render(<ErrorSummary errors={errors} />)

    expect(screen.getByRole('region', { name: 'There is a problem' })).toHaveFocus()
  })

  it('moves focus to the field when an error is followed', () => {
    render(
      <>
        <ErrorSummary errors={errors} />
        <input id="firstName" aria-label="First name" />
      </>,
    )

    screen.getByRole('link', { name: 'Enter a first name' }).click()

    expect(screen.getByRole('textbox', { name: 'First name' })).toHaveFocus()
  })

  it('leaves a link to a field that is not on the page to the browser', () => {
    render(<ErrorSummary errors={errors} />)

    const followed = fireEvent.click(screen.getByRole('link', { name: 'Enter a first name' }))

    expect(followed).toBe(true)
  })

  it('shows an error that is not about one field as text', () => {
    render(<ErrorSummary errors={[{ message: 'Sorry, your profile could not be saved' }]} />)

    expect(screen.getByRole('listitem')).toHaveTextContent('Sorry, your profile could not be saved')
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })

  it('shows nothing without errors', () => {
    const { container } = render(<ErrorSummary errors={[]} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<ErrorSummary errors={errors} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
