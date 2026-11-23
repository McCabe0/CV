'use server'

import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { BASE_PATH } from './basePath'
import type { FitSummary } from './jobs'

const SORRY = 'Sorry, something went wrong. Try again later.'
const GONE = 'This job is no longer listed.'

/** A summary as it stands, or why it could not be asked for or read. */
export type FitState = { summary?: FitSummary; error?: string }

/**
 * Asks why a job fits the candidate. The api writes it once, so asking again gives the same summary,
 * or writes it afresh after it failed. Bound to its job on the page.
 */
export async function askWhyItFits(
  jobId: number,
  _previous: FitState,
  _data: FormData,
): Promise<FitState> {
  const { accessToken } = await requireSession(BASE_PATH)
  try {
    const { data, response } = await apiClient(accessToken).POST('/jobs/{jobId}/fit', {
      params: { path: { jobId } },
    })
    if (data) return { summary: data }
    return { error: response.status === 404 ? GONE : SORRY }
  } catch {
    return { error: SORRY }
  }
}

/** The summary as it stands now: read while the candidate waits for it to be written. */
export async function readWhyItFits(jobId: number): Promise<FitState> {
  const { accessToken } = await requireSession(BASE_PATH)
  try {
    const { data } = await apiClient(accessToken).GET('/jobs/{jobId}/fit', {
      params: { path: { jobId } },
    })
    return data ? { summary: data } : { error: SORRY }
  } catch {
    return { error: SORRY }
  }
}
