'use server'

import { apiClient } from '@cv/api-client'
import { redirectInZone, requireSession } from '@cv/auth/server'
import { BASE_PATH } from './basePath'
import { readAnswers, typedAnswers, type Question } from './cvRequest'

const SORRY = 'Sorry, something went wrong. Try again later.'

export type StartState = { needsProfile?: boolean; error?: string }

/** Asks for a CV, then shows its page. A form action, so it works without javascript. */
export async function startCv(_previous: StartState, _data: FormData): Promise<StartState> {
  const { accessToken } = await requireSession(BASE_PATH)

  let id: number
  try {
    const { data, response } = await apiClient(accessToken).POST('/cv-requests')
    if (!data) return response.status === 409 ? { needsProfile: true } : { error: SORRY }
    id = data.id
  } catch {
    return { error: SORRY }
  }
  return redirectInZone(BASE_PATH, `/requests/${id}`)
}

export type AnswerState = { error?: string; values?: Record<string, string> }

/**
 * Sends the answers, then shows the request being written. Bound to its request and questions on the
 * page. Should the request have moved on already, eg answered in another tab, its page shows where it is.
 */
export async function answerQuestions(
  id: number,
  questions: Question[],
  _previous: AnswerState,
  data: FormData,
): Promise<AnswerState> {
  const { accessToken } = await requireSession(BASE_PATH)
  const failed = { error: SORRY, values: typedAnswers(data, questions) }

  try {
    const { data: answered, response } = await apiClient(accessToken).PUT(
      '/cv-requests/{id}/answers',
      {
        params: { path: { id } },
        body: { answers: readAnswers(data, questions) },
      },
    )
    if (!answered && response.status !== 409) return failed
  } catch {
    return failed
  }
  return redirectInZone(BASE_PATH, `/requests/${id}`)
}
