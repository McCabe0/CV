'use server'

import { apiClient } from '@cv/api-client'
import { redirectInZone, requireSession } from '@cv/auth/server'
import { BASE_PATH } from './basePath'
import { answerErrors, readAnswers, typedAnswers, type Question } from './cvRequest'

const SORRY = 'Sorry, something went wrong. Try again later.'

export type StartState = { needsProfile?: boolean; jobGone?: boolean; error?: string }

/** The job the form names, from the jobs area; none for a general CV. */
function jobIdFrom(data: FormData): number | undefined {
  const jobId = Number(data.get('jobId'))
  return Number.isInteger(jobId) && jobId > 0 ? jobId : undefined
}

/**
 * Asks for a CV, then shows its page: a general one, or one aimed at the job the form names. A form
 * action, so it works without javascript.
 */
export async function startCv(_previous: StartState, data: FormData): Promise<StartState> {
  const { accessToken } = await requireSession(BASE_PATH)
  const jobId = jobIdFrom(data)

  let id: number
  try {
    const api = apiClient(accessToken)
    const { data: started, response } = jobId
      ? await api.POST('/cv-requests', { body: { jobId } })
      : await api.POST('/cv-requests')
    if (!started) {
      if (response.status === 409) return { needsProfile: true }
      return response.status === 404 ? { jobGone: true } : { error: SORRY }
    }
    id = started.id
  } catch {
    return { error: SORRY }
  }
  return redirectInZone(BASE_PATH, `/requests/${id}`)
}

const NOT_ACCEPTED = 'Some answers were not accepted. Check them and try again.'

export type AnswerState = {
  /** About the whole form. */
  error?: string
  /** By field, eg an answer over the limit. */
  errors?: Record<string, string>
  values?: Record<string, string>
}

/**
 * Sends the answers, then shows the request being written. Bound to its request and questions on the
 * page. An answer over the limit is shown on its box, without calling the api. Should the request have
 * moved on already, eg answered in another tab, its page shows where it is.
 */
export async function answerQuestions(
  id: number,
  questions: Question[],
  _previous: AnswerState,
  data: FormData,
): Promise<AnswerState> {
  const { accessToken } = await requireSession(BASE_PATH)
  const values = typedAnswers(data, questions)

  const errors = answerErrors(data, questions)
  if (Object.keys(errors).length > 0) return { errors, values }

  try {
    const { data: answered, response } = await apiClient(accessToken).PUT(
      '/cv-requests/{id}/answers',
      {
        params: { path: { id } },
        body: { answers: readAnswers(data, questions) },
      },
    )
    if (!answered && response.status === 400) return { error: NOT_ACCEPTED, values }
    if (!answered && response.status !== 409) return { error: SORRY, values }
  } catch {
    return { error: SORRY, values }
  }
  return redirectInZone(BASE_PATH, `/requests/${id}`)
}
