import { render, screen } from '@testing-library/react'
import { axe } from 'jest-axe'
import { NotFound } from './NotFound'

describe('NotFound', () => {
  it("says the page is not found, and how to check the address, as gov.uk's does", () => {
    render(<NotFound />)

    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument()
    expect(
      screen.getByText('If you typed the web address, check it is correct.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('If you pasted the web address, check you copied the entire address.'),
    ).toBeInTheDocument()
  })

  it('links back to the home page', () => {
    render(<NotFound />)

    expect(screen.getByRole('link', { name: 'Go to the home page' })).toHaveAttribute('href', '/')
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<NotFound />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
