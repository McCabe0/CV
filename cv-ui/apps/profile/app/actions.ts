'use server'

import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { BASE_PATH } from './basePath'
import { readForm, validate, valuesFrom, type ProfileFormState } from './profileValues'

const NOT_ACCEPTED = 'Your profile was not accepted. Check your answers and try again.'
const FAILED = 'Sorry, your profile could not be saved. Try again later.'

/**
 * Saves the profile, or returns the form with what is wrong. A form action, so it works without
 * javascript. Saving again is safe: the api replaces the profile, so no redirect is needed.
 */
export async function saveProfile(
  _previous: ProfileFormState,
  data: FormData,
): Promise<ProfileFormState> {
  const { accessToken } = await requireSession(BASE_PATH)
  const values = readForm(data)

  const result = validate(values)
  if (!result.ok) return { values, errors: result.errors }

  try {
    const { data: profile, response } = await apiClient(accessToken).PUT('/profile', {
      body: result.request,
    })
    if (!profile)
      return { values, errors: {}, formError: response.status === 400 ? NOT_ACCEPTED : FAILED }
    // What the api kept, eg a skill given twice is saved once.
    return { values: valuesFrom(profile), errors: {}, saved: true }
  } catch {
    return { values, errors: {}, formError: FAILED }
  }
}
