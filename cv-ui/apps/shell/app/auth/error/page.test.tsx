import { render, screen } from '@testing-library/react'
import { axe } from 'jest-axe'
import SignInError from './page'

describe('sign in error page', () => {
  it('says sign in failed and lets the user try again', () => {
    render(<SignInError />)

    expect(screen.getByRole('heading', { level: 1, name: 'Sign in failed' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Try signing in again' })).toHaveAttribute(
      'href',
      '/auth/login',
    )
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<SignInError />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
