import { render, screen } from '@testing-library/react'
import Page, { metadata } from './page'

jest.mock('./actions', () => ({ startCv: jest.fn() }))

describe('cv home page', () => {
  it('explains what happens, and offers to write a CV', () => {
    render(<Page />)

    expect(metadata.title).toBe('Your CV — Skill2Career')
    expect(screen.getByRole('heading', { level: 1, name: 'Your CV' })).toBeInTheDocument()
    expect(screen.getByText(/written only from your profile/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Write my CV' })).toBeInTheDocument()
  })
})
