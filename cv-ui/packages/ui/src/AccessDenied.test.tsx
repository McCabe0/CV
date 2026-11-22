import { render, screen } from '@testing-library/react'
import { axe } from 'jest-axe'
import { AccessDenied } from './AccessDenied'

describe('AccessDenied', () => {
  it('explains that the account cannot use this workspace, without showing anything from it', () => {
    render(<AccessDenied workspace="the operational workspace" />)

    expect(
      screen.getByRole('heading', { level: 1, name: 'You cannot access this' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Your account does not have access to the operational workspace.'),
    ).toBeInTheDocument()
  })

  it('links back to the home page', () => {
    render(<AccessDenied workspace="the operational workspace" />)

    expect(screen.getByRole('link', { name: 'Go to the home page' })).toHaveAttribute('href', '/')
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<AccessDenied workspace="the operational workspace" />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
