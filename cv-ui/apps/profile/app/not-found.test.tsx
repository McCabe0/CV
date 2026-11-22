import { render, screen } from '@testing-library/react'
import NotFoundPage, { metadata } from './not-found'

describe('not found page', () => {
  it('is titled and headed page not found', () => {
    render(<NotFoundPage />)

    expect(metadata.title).toBe('Page not found')
    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument()
  })
})
