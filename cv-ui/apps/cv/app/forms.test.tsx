import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'jest-axe'
import { AnswerForm, StartCvForm } from './forms'

describe('StartCvForm', () => {
  it('asks for a CV', async () => {
    const action = jest.fn(async () => ({}))
    render(<StartCvForm action={action} />)

    await userEvent.click(screen.getByRole('button', { name: 'Write my CV' }))

    expect(action).toHaveBeenCalled()
  })

  it('sends the candidate to their profile when they have none', () => {
    render(<StartCvForm action={jest.fn()} initialState={{ needsProfile: true }} />)

    expect(screen.getByRole('alert')).toHaveTextContent('Save your profile before asking for a CV.')
    // The profile is another zone, so a plain link.
    expect(screen.getByRole('link', { name: 'Go to your profile' })).toHaveAttribute(
      'href',
      '/profile',
    )
  })

  it('says sorry when it went wrong', () => {
    render(<StartCvForm action={jest.fn()} initialState={{ error: 'Sorry.' }} />)

    expect(screen.getByRole('alert')).toHaveTextContent('Sorry.')
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(
      <StartCvForm action={jest.fn()} initialState={{ needsProfile: true }} />,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})

describe('AnswerForm', () => {
  const questions = [
    { id: 1, text: 'By how much did you cut settlement time?' },
    { id: 2, text: 'Which team were you in?' },
  ]

  it('asks each question, each optional', () => {
    render(<AnswerForm questions={questions} action={jest.fn()} />)

    expect(
      screen.getByRole('textbox', { name: 'By how much did you cut settlement time? (optional)' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('textbox', { name: 'Which team were you in? (optional)' }),
    ).toBeInTheDocument()
    expect(screen.getAllByText('Leave it blank to skip.')).toHaveLength(2)
  })

  it('sends the answers', async () => {
    const action = jest.fn(async () => ({}))
    render(<AnswerForm questions={questions} action={action} />)

    await userEvent.type(screen.getByRole('textbox', { name: /settlement/ }), 'By 40%')
    await userEvent.click(screen.getByRole('button', { name: 'Send answers' }))

    const data = (action.mock.calls[0] as unknown as [object, FormData])[1]
    expect(data.get('answer-1')).toBe('By 40%')
  })

  it('keeps what was typed, and says sorry, when sending failed', () => {
    render(
      <AnswerForm
        questions={questions}
        action={jest.fn()}
        initialState={{ error: 'Sorry.', values: { 'answer-1': 'By 40%' } }}
      />,
    )

    expect(screen.getByRole('textbox', { name: /settlement/ })).toHaveValue('By 40%')
    expect(screen.getByText('Sorry.')).toBeInTheDocument()
  })

  it('has no detectable accessibility violations', async () => {
    const { container } = render(<AnswerForm questions={questions} action={jest.fn()} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
