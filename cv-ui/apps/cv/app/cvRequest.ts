import type { components } from '@cv/api-client'

export type CvRequest = components['schemas']['CvRequestResponse']
export type Question = components['schemas']['QuestionResponse']
export type Round = components['schemas']['RoundResponse']
export type Cv = components['schemas']['Cv']
export type Status = CvRequest['status']

/** Completed or failed: nothing more will change. */
export const isFinished = (status: Status) => status === 'COMPLETED' || status === 'FAILED'

/** Where the request is, in a sentence the candidate reads while they wait. */
export function statusLine(request: CvRequest): string {
  const last = request.rounds.at(-1)
  switch (request.status) {
    case 'REQUESTED':
      return 'The interviewer is reading your profile.'
    case 'AWAITING_ANSWERS':
      return 'A few questions will make your CV stronger.'
    case 'WRITING':
      return last
        ? `The critic scored draft ${last.number} at ${last.score} out of 100. The writer is improving it.`
        : 'The writer is drafting your CV.'
    case 'COMPLETED':
      return 'Your CV is ready.'
    case 'FAILED':
      return 'Sorry, your CV could not be written.'
  }
}

/** The form field for a question's answer. */
export const answerField = (question: Question) => `answer-${question.id}`

/** An answer to each question, trimmed. A blank one is a skip, which is always allowed. */
export function readAnswers(data: FormData, questions: Question[]) {
  return questions.map((question) => {
    const text = String(data.get(answerField(question)) ?? '').trim()
    return { questionId: question.id, text: text || null }
  })
}

/** What was typed, to give back with an error. */
export function typedAnswers(data: FormData, questions: Question[]): Record<string, string> {
  return Object.fromEntries(
    questions.map((question) => [
      answerField(question),
      String(data.get(answerField(question)) ?? ''),
    ]),
  )
}
