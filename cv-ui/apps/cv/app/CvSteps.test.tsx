import { render, screen, within } from '@testing-library/react'
import { axe } from 'jest-axe'
import { CvSteps } from './CvSteps'

const steps = () => within(screen.getByRole('list', { name: "Your CV's progress" }))
const step = (name: string) => steps().getByText(name).closest('li')!

describe('CvSteps', () => {
  it('lists the four steps of a CV, in order', () => {
    render(<CvSteps status="REQUESTED" />)

    expect(
      steps()
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual([
      expect.stringContaining('Interview'),
      expect.stringContaining('Your answers'),
      expect.stringContaining('Writing'),
      expect.stringContaining('Ready'),
    ])
  })

  it.each([
    ['REQUESTED', 'Interview'],
    ['AWAITING_ANSWERS', 'Your answers'],
    ['WRITING', 'Writing'],
    ['COMPLETED', 'Ready'],
  ] as const)('marks where a %s request is', (status, current) => {
    render(<CvSteps status={status} />)

    expect(step(current)).toHaveAttribute('aria-current', 'step')
    expect(
      steps()
        .getAllByRole('listitem')
        .filter((item) => item.hasAttribute('aria-current')),
    ).toHaveLength(1)
  })

  it('says which steps are done, for screen readers', () => {
    render(<CvSteps status="WRITING" />)

    expect(step('Interview')).toHaveTextContent('Done: Interview')
    expect(step('Your answers')).toHaveTextContent('Done: Your answers')
    expect(step('Writing')).not.toHaveTextContent('Done')
  })

  it('counts a ready CV as done', () => {
    render(<CvSteps status="COMPLETED" />)

    expect(step('Ready')).toHaveTextContent('Done: Ready')
  })

  it('shows nothing for a failed request', () => {
    const { container } = render(<CvSteps status="FAILED" />)

    expect(container).toBeEmptyDOMElement()
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<CvSteps status="WRITING" />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
