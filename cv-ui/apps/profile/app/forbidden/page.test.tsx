import { render, screen } from '@testing-library/react'
import Forbidden, { metadata } from './page'

describe('forbidden page', () => {
  it('says the account cannot use candidate profiles', () => {
    render(<Forbidden />)

    expect(metadata.title).toBe('You cannot access this')
    expect(
      screen.getByText('Your account does not have access to candidate profiles.'),
    ).toBeInTheDocument()
  })
})
