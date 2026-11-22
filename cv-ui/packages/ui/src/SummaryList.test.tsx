import { render, screen } from '@testing-library/react'
import { axe } from 'jest-axe'
import { SummaryList } from './SummaryList'

const rows = [
  { key: 'Status', value: 'Submitted' },
  { key: 'Email address', value: undefined },
  { key: 'Address', value: '' },
]

describe('SummaryList', () => {
  it('pairs each label with its value', () => {
    render(<SummaryList rows={[{ key: 'Status', value: 'Submitted' }]} />)

    expect(screen.getByRole('term')).toHaveTextContent('Status')
    expect(screen.getByRole('definition')).toHaveTextContent('Submitted')
  })

  it('says when a value was not given, rather than showing nothing', () => {
    render(<SummaryList rows={rows} />)

    expect(screen.getAllByRole('definition').map((d) => d.textContent)).toEqual([
      'Submitted',
      'Not given',
      'Not given',
    ])
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<SummaryList rows={rows} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
