import { answerErrors, isFinished, readAnswers, statusLine } from './cvRequest'
import { cvRequest, round } from './fixtures'

describe('statusLine', () => {
  it.each([
    [cvRequest({ status: 'REQUESTED' }), 'The interviewer is reading your profile.'],
    [cvRequest({ status: 'AWAITING_ANSWERS' }), 'A few questions will make your CV stronger.'],
    [cvRequest({ status: 'WRITING' }), 'The writer is drafting your CV.'],
    [
      cvRequest({ status: 'WRITING', rounds: [round(1, 64)] }),
      'The critic scored draft 1 at 64 out of 100. The writer is improving it.',
    ],
    [cvRequest({ status: 'COMPLETED', rounds: [round(1, 91, true)] }), 'Your CV is ready.'],
    [cvRequest({ status: 'FAILED' }), 'Sorry, your CV could not be written.'],
  ])('says where the request is', (request, line) => {
    expect(statusLine(request)).toBe(line)
  })
})

describe('isFinished', () => {
  it('is finished once completed or failed, as nothing more will change', () => {
    expect(isFinished('COMPLETED')).toBe(true)
    expect(isFinished('FAILED')).toBe(true)
    expect(isFinished('WRITING')).toBe(false)
    expect(isFinished('AWAITING_ANSWERS')).toBe(false)
  })
})

describe('readAnswers', () => {
  it('reads an answer to each question, trimmed, a blank one being a skip', () => {
    const data = new FormData()
    data.set('answer-1', '  By 40%  ')
    data.set('answer-2', '   ')

    expect(
      readAnswers(data, [
        { id: 1, text: 'By how much?' },
        { id: 2, text: 'Which team?' },
        { id: 3, text: 'Left out of the form?' },
      ]),
    ).toEqual([
      { questionId: 1, text: 'By 40%' },
      { questionId: 2, text: null },
      { questionId: 3, text: null },
    ])
  })
})

describe('answerErrors', () => {
  const questions = [
    { id: 1, text: 'By how much?' },
    { id: 2, text: 'Which team?' },
  ]

  it('finds each answer over 2,000 characters, saying how long it is', () => {
    const data = new FormData()
    data.set('answer-1', 'a'.repeat(2340))
    data.set('answer-2', 'a'.repeat(2000))

    expect(answerErrors(data, questions)).toEqual({
      'answer-1': 'Keep this answer to 2,000 characters. It has 2,340.',
    })
  })

  it('counts the answer as it will be sent, trimmed', () => {
    const data = new FormData()
    data.set('answer-1', `  ${'a'.repeat(2000)}  `)

    expect(answerErrors(data, questions)).toEqual({})
  })
})
