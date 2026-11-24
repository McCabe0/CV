import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { CompanyActions } from './CompanyActions'

const done = (message: string) => jest.fn(async () => ({ message }))

describe('CompanyActions', () => {
  it('lets an admin crawl a tracked company now, saying what happens next', async () => {
    const crawl = done('Crawling Monzo now.')
    render(<CompanyActions name="Monzo" crawl={crawl} stop={jest.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Crawl Monzo now' }))

    expect(crawl).toHaveBeenCalled()
    expect(await screen.findByRole('status')).toHaveTextContent('Crawling Monzo now.')
  })

  it('lets an admin stop tracking a tracked company', async () => {
    const stop = done('Stopped tracking Monzo.')
    render(<CompanyActions name="Monzo" crawl={jest.fn()} stop={stop} />)

    await userEvent.click(screen.getByRole('button', { name: 'Stop tracking Monzo' }))

    expect(stop).toHaveBeenCalled()
    expect(await screen.findByRole('status')).toHaveTextContent('Stopped tracking Monzo.')
  })

  it('offers only to track an untracked company again', async () => {
    const trackAgain = done('Tracking Monzo again.')
    render(<CompanyActions name="Monzo" trackAgain={trackAgain} />)

    expect(screen.queryByRole('button', { name: /Crawl/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Stop/ })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Track Monzo again' }))

    expect(trackAgain).toHaveBeenCalled()
    expect(await screen.findByRole('status')).toHaveTextContent('Tracking Monzo again.')
  })

  it('says why a button did not work', async () => {
    const crawl = jest.fn(async () => ({ error: 'This company is no longer tracked.' }))
    render(<CompanyActions name="Monzo" crawl={crawl} stop={jest.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Crawl Monzo now' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This company is no longer tracked.')
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<CompanyActions name="Monzo" crawl={jest.fn()} stop={jest.fn()} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
