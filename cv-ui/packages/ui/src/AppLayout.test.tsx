import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { AppLayout } from './AppLayout'

const renderLayout = () =>
  render(
    <AppLayout>
      <h1>Your profile</h1>
    </AppLayout>,
  )

describe('AppLayout', () => {
  it('names the service in the banner, as a link home', () => {
    renderLayout()

    const banner = within(screen.getByRole('banner'))
    expect(banner.getByRole('link', { name: 'Skill2Career' })).toHaveAttribute('href', '/')
  })

  it('has no main navigation when there are no areas to go to', () => {
    renderLayout()

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('renders the page content inside the main landmark', () => {
    renderLayout()

    expect(screen.getByRole('main')).toContainElement(
      screen.getByRole('heading', { level: 1, name: 'Your profile' }),
    )
  })

  it('offers a skip link as the first focusable element, which moves focus to the main content', async () => {
    const user = userEvent.setup()
    renderLayout()

    await user.tab()
    const skipLink = screen.getByRole('link', { name: 'Skip to main content' })
    expect(skipLink).toHaveFocus()

    await user.keyboard('{Enter}')
    expect(screen.getByRole('main')).toHaveFocus()
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = renderLayout()

    expect(await axe(container)).toHaveNoViolations()
  })

  describe('when signed in', () => {
    const renderSignedIn = () =>
      render(
        <AppLayout user={{ name: 'staff test' }}>
          <h1>Operational workspace</h1>
        </AppLayout>,
      )

    it('says who is signed in', () => {
      renderSignedIn()

      expect(screen.getByRole('banner')).toHaveTextContent('Signed in as staff test')
    })

    it('signs out with a post to the shell, so a link or prefetch can never sign someone out', () => {
      renderSignedIn()

      const signOut = screen.getByRole('button', { name: 'Sign out' })
      expect(signOut).toHaveAttribute('type', 'submit')
      expect(signOut.closest('form')).toHaveAttribute('action', '/auth/logout')
      expect(signOut.closest('form')).toHaveAttribute('method', 'post')
    })

    it('has no detectable accessibility violations', async () => {
      const { container } = renderSignedIn()

      expect(await axe(container)).toHaveNoViolations()
    })
  })

  describe('with areas', () => {
    const areas = [
      { name: 'Your profile', href: '/profile' },
      { name: 'Your CV', href: '/cv' },
    ]
    const renderWithAreas = () =>
      render(
        <AppLayout current="/cv" areas={areas} user={{ name: 'Casey Candidate' }}>
          <h1>Your CV</h1>
        </AppLayout>,
      )
    const nav = () => within(screen.getByRole('navigation', { name: 'Main' }))

    it('links home, then to each area, as plain links for a full page load between zones', () => {
      renderWithAreas()

      const links = nav().getAllByRole('link')
      expect(links.map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
        ['Home', '/'],
        ['Your profile', '/profile'],
        ['Your CV', '/cv'],
      ])
    })

    it('marks the area the page belongs to as current, and only that one', () => {
      renderWithAreas()

      expect(nav().getByRole('link', { name: 'Your CV' })).toHaveAttribute('aria-current', 'true')
      expect(nav().getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
      expect(nav().getByRole('link', { name: 'Your profile' })).not.toHaveAttribute('aria-current')
    })

    it('shows the signed in user by their initials too, hidden from screen readers', () => {
      renderWithAreas()

      expect(screen.getByText('CC')).toHaveAttribute('aria-hidden', 'true')
    })

    it('has no detectable accessibility violations', async () => {
      const { container } = renderWithAreas()

      expect(await axe(container)).toHaveNoViolations()
    })
  })

  it('offers no sign out when signed out', () => {
    renderLayout()

    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  })
})
