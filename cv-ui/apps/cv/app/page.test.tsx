import { render, screen } from '@testing-library/react'
import Page, { metadata } from './page'

jest.mock('./actions', () => ({ startCv: jest.fn() }))

const renderPage = async (searchParams: Record<string, string> = {}) =>
  render(await Page({ searchParams: Promise.resolve(searchParams) }))

describe('cv home page', () => {
  it('explains what happens, and offers to write a CV', async () => {
    await renderPage()

    expect(metadata.title).toBe('Your CV — Skill2Career')
    expect(screen.getByRole('heading', { level: 1, name: 'Your CV' })).toBeInTheDocument()
    expect(screen.getByText(/written only from your profile/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Write my CV' })).toBeInTheDocument()
  })

  it('offers a CV aimed at the job chosen in the jobs area', async () => {
    await renderPage({ job: '42' })

    expect(screen.getByRole('heading', { level: 1, name: 'A CV for one job' })).toBeInTheDocument()
    expect(screen.getByText(/aimed at the job you chose/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Write a CV for this job' })).toBeInTheDocument()
  })

  it('offers a general CV when the job is not a job id', async () => {
    await renderPage({ job: '-3' })

    expect(screen.getByRole('button', { name: 'Write my CV' })).toBeInTheDocument()
  })
})
