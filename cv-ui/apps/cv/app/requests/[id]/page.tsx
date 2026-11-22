import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { answerQuestions, startCv } from '../../actions'
import { BASE_PATH } from '../../basePath'
import { CvDocument } from '../../CvDocument'
import { CvProgress } from '../../CvProgress'
import type { CvRequest } from '../../cvRequest'
import { AnswerForm, StartCvForm } from '../../forms'
import { Rounds } from '../../Rounds'

export const metadata: Metadata = { title: 'Your CV' }

type Props = { params: Promise<{ id: string }> }

/** One CV request, shown as its status needs: the questions, the agents at work, or the CV. */
export default async function Page({ params }: Props) {
  const id = Number((await params).id)
  if (!Number.isInteger(id) || id <= 0) notFound()

  const { user, accessToken } = await requireSession(`${BASE_PATH}/requests/${id}`)
  const loaded = await load(accessToken, id)
  if (loaded === 'not-found') notFound()
  if (loaded === 'failed') {
    return (
      <>
        <h1>Your CV</h1>
        <p role="alert">Sorry, this CV cannot be shown at the moment. Try again later.</p>
      </>
    )
  }

  const { request, person } = loaded
  switch (request.status) {
    case 'AWAITING_ANSWERS':
      return (
        <>
          <h1>A few questions</h1>
          <p>
            Your answers make your CV stronger, and are saved to your profile for your next CV. Skip
            any you would rather not answer.
          </p>
          <AnswerForm
            questions={request.questions}
            action={answerQuestions.bind(null, id, request.questions)}
          />
        </>
      )
    case 'COMPLETED':
      return (
        <>
          <h1>Your CV</h1>
          <CvDocument cv={request.cv!} person={person ?? { name: user.name }} />
          <Rounds rounds={request.rounds} />
        </>
      )
    case 'FAILED':
      return (
        <>
          <h1>Your CV could not be written</h1>
          <p>{request.failure}</p>
          <StartCvForm action={startCv} />
        </>
      )
    default:
      return (
        <>
          <h1>Writing your CV</h1>
          <CvProgress initial={request} />
        </>
      )
  }
}

/** The request, and the candidate's profile for the CV's heading. */
async function load(accessToken: string, id: number) {
  try {
    const api = apiClient(accessToken)
    const [{ data, response }, profile] = await Promise.all([
      api.GET('/cv-requests/{id}', { params: { path: { id } } }),
      api.GET('/profile'),
    ])
    if (!data) return response.status === 404 ? 'not-found' : 'failed'
    return { request: data as CvRequest, person: profile.data }
  } catch {
    return 'failed'
  }
}
