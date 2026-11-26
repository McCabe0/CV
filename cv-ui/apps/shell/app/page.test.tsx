import { render, screen, within } from '@testing-library/react'
import { getSession } from '@cv/auth/server'
import { axe } from 'jest-axe'
import Home from './page'

jest.mock('@cv/auth/server', () => ({ getSession: jest.fn() }))

const signedInAs = (...roles: string[]) =>
  jest.mocked(getSession).mockResolvedValue({
    user: { username: 'casey', name: 'Casey', roles },
    accessToken: 'access',
    refreshToken: 'refresh',
    expiresAt: 0,
  })

const renderHome = async () => render(await Home())

describe('shell home page', () => {
  it('welcomes the user and links to what they can use', async () => {
    signedInAs('CANDIDATE')
    await renderHome()

    expect(screen.getByRole('heading', { level: 1, name: 'Welcome, Casey' })).toBeInTheDocument()
    const areas = within(screen.getByRole('navigation', { name: 'Your areas' }))
    // Each area is a separate zone, so the link is a plain one, for a full page load.
    expect(areas.getByRole('link', { name: 'Your profile' })).toHaveAttribute('href', '/profile')
  })

  it('says so when the account can use nothing', async () => {
    signedInAs()
    await renderHome()

    expect(
      screen.getByText('Your account does not have access to anything yet.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('offers nothing with no session', async () => {
    jest.mocked(getSession).mockResolvedValue(null)
    await renderHome()

    expect(screen.getByRole('heading', { level: 1, name: 'Skill2Career' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('offers sign in with no session', async () => {
    jest.mocked(getSession).mockResolvedValue(null)
    await renderHome()

    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/auth/login')
  })

  it('says what each area is for', async () => {
    signedInAs('CANDIDATE')
    await renderHome()

    expect(
      screen.getByText('Roles that fit your profile, and why each one fits.'),
    ).toBeInTheDocument()
  })

  it('has no detectable accessibility violations', async () => {
    signedInAs('CANDIDATE')
    const { container } = await renderHome()

    expect(await axe(container)).toHaveNoViolations()
  })
})
