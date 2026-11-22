import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SkipLink } from './SkipLink'

describe('SkipLink', () => {
  it('links to its target', () => {
    render(<SkipLink targetId="content" />)

    expect(screen.getByRole('link', { name: 'Skip to main content' })).toHaveAttribute(
      'href',
      '#content',
    )
  })

  it('moves focus to the target when used', async () => {
    const user = userEvent.setup()
    render(
      <>
        <SkipLink targetId="content" />
        <div id="content" tabIndex={-1} data-testid="target" />
      </>,
    )

    await user.click(screen.getByRole('link', { name: 'Skip to main content' }))

    expect(screen.getByTestId('target')).toHaveFocus()
  })

  it('leaves focus on the link when the target is missing', async () => {
    const user = userEvent.setup()
    render(<SkipLink targetId="missing" />)
    const link = screen.getByRole('link', { name: 'Skip to main content' })

    await user.click(link)

    expect(link).toHaveFocus()
  })
})
