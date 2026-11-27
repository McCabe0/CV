import { act, render, screen, within } from '@testing-library/react'
import { axe } from 'jest-axe'
import { useRouter } from 'next/navigation'
import { CvProgress } from './CvProgress'
import { cvRequest, round } from './fixtures'

jest.mock('next/navigation', () => ({ useRouter: jest.fn() }))

/** The browser's EventSource, played by the test. */
class FakeEventSource {
  static last: FakeEventSource
  listeners: Record<string, (event: MessageEvent) => void> = {}
  closed = false

  constructor(public url: string) {
    FakeEventSource.last = this
  }

  addEventListener(name: string, listener: (event: MessageEvent) => void) {
    this.listeners[name] = listener
  }

  close() {
    this.closed = true
  }

  emit(request: object) {
    act(() =>
      this.listeners.progress!(new MessageEvent('progress', { data: JSON.stringify(request) })),
    )
  }
}

const refresh = jest.fn()

beforeEach(() => {
  ;(global as unknown as { EventSource: unknown }).EventSource = FakeEventSource
  jest.mocked(useRouter).mockReturnValue({ refresh } as unknown as ReturnType<typeof useRouter>)
})

describe('CvProgress', () => {
  it('follows the request on its stream, in this zone', () => {
    render(<CvProgress initial={cvRequest({ status: 'WRITING' })} />)

    expect(FakeEventSource.last.url).toBe('/cv/requests/7/events')
    expect(screen.getByRole('status')).toHaveTextContent('The writer is drafting your CV.')
  })

  it('shows each round as the critic reviews it', () => {
    render(<CvProgress initial={cvRequest({ status: 'WRITING' })} />)

    FakeEventSource.last.emit(cvRequest({ status: 'WRITING', rounds: [round(1, 64)] }))

    expect(screen.getByRole('status')).toHaveTextContent(
      'The critic scored draft 1 at 64 out of 100.',
    )
    const rounds = within(screen.getByRole('region', { name: 'The writer and the critic' }))
    expect(rounds.getByRole('listitem')).toHaveTextContent('Draft 1: 64 out of 100')
    expect(rounds.getByRole('listitem')).toHaveTextContent('Add numbers to the bullets')
    expect(refresh).not.toHaveBeenCalled()
  })

  it.each(['AWAITING_ANSWERS', 'COMPLETED', 'FAILED'] as const)(
    'reloads the page when the request needs the candidate, or is finished: %s',
    (status) => {
      render(<CvProgress initial={cvRequest({ status: 'REQUESTED' })} />)

      FakeEventSource.last.emit(cvRequest({ status }))

      expect(refresh).toHaveBeenCalled()
      expect(FakeEventSource.last.closed).toBe(true)
    },
  )

  it('moves the progress steps on as the request does', () => {
    render(<CvProgress initial={cvRequest({ status: 'REQUESTED' })} />)
    const steps = screen.getByRole('list', { name: "Your CV's progress" })
    expect(within(steps).getByText('Interview').closest('li')).toHaveAttribute(
      'aria-current',
      'step',
    )

    FakeEventSource.last.emit(cvRequest({ status: 'WRITING' }))

    expect(within(steps).getByText('Writing').closest('li')).toHaveAttribute('aria-current', 'step')
  })

  it('stops following when the page is left', () => {
    const { unmount } = render(<CvProgress initial={cvRequest({ status: 'WRITING' })} />)

    unmount()

    expect(FakeEventSource.last.closed).toBe(true)
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(
      <CvProgress initial={cvRequest({ status: 'WRITING', rounds: [round(1, 64)] })} />,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
