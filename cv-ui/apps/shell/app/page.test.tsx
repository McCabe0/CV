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

  it('says what it does, and offers no areas, with no session', async () => {
    jest.mocked(getSession).mockResolvedValue(null)
    await renderHome()

    expect(
      screen.getByRole('heading', { level: 1, name: 'A CV written for you, and the jobs it fits' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('offers sign in with no session, at the top and the end', async () => {
    jest.mocked(getSession).mockResolvedValue(null)
    await renderHome()

    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/auth/login')
    expect(screen.getByRole('link', { name: 'Get started' })).toHaveAttribute('href', '/auth/login')
  })

  it('shows how it works, in order, with no session', async () => {
    jest.mocked(getSession).mockResolvedValue(null)
    await renderHome()

    const section = within(screen.getByRole('region', { name: 'How it works' }))
    const steps = section.getAllByRole('listitem')
    expect(steps).toHaveLength(3)
    expect(steps[0]).toHaveTextContent('Tell us about you')
    expect(steps[1]).toHaveTextContent('Your CV team gets to work')
    expect(steps[2]).toHaveTextContent('See the jobs that fit')
  })

  it('introduces the three agents with no session', async () => {
    jest.mocked(getSession).mockResolvedValue(null)
    await renderHome()

    const team = within(screen.getByRole('region', { name: 'Meet your CV team' }))
    for (const agent of ['The interviewer', 'The writer', 'The critic']) {
      expect(team.getByRole('heading', { level: 3, name: agent })).toBeInTheDocument()
    }
  })

  it('says where a profile is sent, with no session', async () => {
    jest.mocked(getSession).mockResolvedValue(null)
    await renderHome()

    expect(screen.getByText(/sent to Google's Gemini/)).toBeInTheDocument()
  })

  it('has no detectable accessibility violations with no session', async () => {
    jest.mocked(getSession).mockResolvedValue(null)
    const { container } = await renderHome()

    expect(await axe(container)).toHaveNoViolations()
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
