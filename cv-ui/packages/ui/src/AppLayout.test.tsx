import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { AppLayout } from './AppLayout'

const renderLayout = () =>
  render(
    <AppLayout area="Profile">
      <h1>Your profile</h1>
    </AppLayout>,
  )

describe('AppLayout', () => {
  it('names the service and the area in the banner', () => {
    renderLayout()

    expect(screen.getByRole('banner')).toHaveTextContent('Skill2Career · Profile')
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
        <AppLayout area="Staff" user={{ name: 'staff test' }}>
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

  it('offers no sign out when signed out', () => {
    renderLayout()

    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  })
})
