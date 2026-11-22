import { render, screen, within } from '@testing-library/react'
import { axe } from 'jest-axe'
import { CvDocument } from './CvDocument'
import { draft } from './fixtures'

const person = {
  name: 'Casey Candidate',
  email: 'casey@example.com',
  phone: '+353 1 234 5678',
  location: 'Dublin',
  linkedin: 'https://linkedin.com/in/casey',
}

describe('CvDocument', () => {
  it('heads the CV with the candidate and how to reach them', () => {
    render(<CvDocument cv={draft} person={person} />)

    expect(screen.getByRole('heading', { level: 2, name: 'Casey Candidate' })).toBeInTheDocument()
    expect(screen.getByText(/Dublin/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'casey@example.com' })).toHaveAttribute(
      'href',
      'mailto:casey@example.com',
    )
    expect(screen.getByRole('link', { name: 'https://linkedin.com/in/casey' })).toHaveAttribute(
      'href',
      'https://linkedin.com/in/casey',
    )
  })

  it('shows each section the writer filled', () => {
    render(<CvDocument cv={draft} person={person} />)

    expect(screen.getByText(draft.summary)).toBeInTheDocument()
    const experience = within(screen.getByRole('region', { name: 'Experience' }))
    expect(
      experience.getByRole('heading', { level: 4, name: 'Software Engineer, Acme Payments' }),
    ).toBeInTheDocument()
    expect(experience.getByText('2021 – now')).toBeInTheDocument()
    expect(experience.getByText('Cut settlement time by 40%')).toBeInTheDocument()
    for (const section of ['Skills', 'Education', 'Projects', 'Certifications', 'Languages']) {
      expect(screen.getByRole('region', { name: section })).toBeInTheDocument()
    }
    expect(screen.getByText('Kotlin')).toBeInTheDocument()
  })

  it('leaves out sections the writer left empty, and contact details not given', () => {
    render(
      <CvDocument
        cv={{ ...draft, projects: [], certifications: [], languages: [] }}
        person={{ name: 'Casey' }}
      />,
    )

    expect(screen.queryByRole('region', { name: 'Projects' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Certifications' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Languages' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<CvDocument cv={draft} person={person} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
