import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { CHECK_EVERY_MS, MAX_CHECKS, WhyItFits } from './WhyItFits'

const writing = { summary: { jobId: 42, status: 'WRITING' } }
const ready = { summary: { jobId: 42, status: 'READY', text: 'Your Kotlin fits.' } }
const failed = { summary: { jobId: 42, status: 'FAILED', failure: 'The AI was too busy.' } }

beforeEach(() => jest.useFakeTimers())
afterEach(() => jest.useRealTimers())

const user = () => userEvent.setup({ advanceTimers: jest.advanceTimersByTime })

/** Lets the next check run, and its answer arrive. */
const nextCheck = () => act(async () => jest.advanceTimersByTime(CHECK_EVERY_MS))

describe('WhyItFits', () => {
  it('offers to say why the job fits, asking only when the candidate does', async () => {
    const ask = jest.fn(async () => ready)
    render(<WhyItFits ask={ask} read={jest.fn()} />)

    expect(ask).not.toHaveBeenCalled()
    await user().click(screen.getByRole('button', { name: 'Why it fits' }))

    expect(ask).toHaveBeenCalled()
    expect(await screen.findByText('Your Kotlin fits.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('says it is being written, and checks back until it is ready', async () => {
    const read = jest.fn().mockResolvedValueOnce(writing).mockResolvedValueOnce(ready)
    render(<WhyItFits ask={jest.fn(async () => writing)} read={read} />)

    await user().click(screen.getByRole('button', { name: 'Why it fits' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Working out why this job fits you')

    await nextCheck()
    expect(read).toHaveBeenCalledTimes(1)
    await nextCheck()

    expect(read).toHaveBeenCalledTimes(2)
    expect(screen.getByText('Your Kotlin fits.')).toBeInTheDocument()
    await nextCheck()
    expect(read).toHaveBeenCalledTimes(2)
  })

  it('says why it failed, and lets the candidate ask again', async () => {
    const ask = jest.fn().mockResolvedValueOnce(failed).mockResolvedValueOnce(ready)
    render(<WhyItFits ask={ask} read={jest.fn()} />)

    await user().click(screen.getByRole('button', { name: 'Why it fits' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The AI was too busy.')

    await user().click(screen.getByRole('button', { name: 'Ask again' }))
    expect(await screen.findByText('Your Kotlin fits.')).toBeInTheDocument()
  })

  it('says sorry when it could not ask, and lets the candidate try again', async () => {
    render(<WhyItFits ask={jest.fn(async () => ({ error: 'Sorry.' }))} read={jest.fn()} />)

    await user().click(screen.getByRole('button', { name: 'Why it fits' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Sorry.')
    expect(screen.getByRole('button', { name: 'Ask again' })).toBeInTheDocument()
  })

  it('stops checking after a while, saying to come back later', async () => {
    const read = jest.fn(async () => writing)
    render(<WhyItFits ask={jest.fn(async () => writing)} read={read} />)
    await user().click(screen.getByRole('button', { name: 'Why it fits' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Working out why this job fits you')

    for (let i = 0; i < MAX_CHECKS + 2; i++) await nextCheck()

    expect(read).toHaveBeenCalledTimes(MAX_CHECKS)
    expect(screen.getByRole('status')).toHaveTextContent('taking longer than usual')
    expect(screen.getByRole('button', { name: 'Check again' })).toBeInTheDocument()
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(
      <WhyItFits ask={jest.fn()} read={jest.fn()} initialState={failed} />,
    )

    jest.useRealTimers()
    expect(await axe(container)).toHaveNoViolations()
  })
})
