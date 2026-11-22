import type { Metadata } from 'next'
import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { saveProfile } from './actions'
import { BASE_PATH } from './basePath'
import { ProfileForm } from './ProfileForm'
import { emptyValues, valuesFrom, type ProfileValues } from './profileValues'

// The layout's title template only applies to nested pages, so the zone's root gives its full title.
export const metadata: Metadata = { title: 'Your profile — Skill2Career' }

export default async function Page() {
  const { user, accessToken } = await requireSession(BASE_PATH)
  const loaded = await loadProfile(accessToken)

  return (
    <>
      <h1>Your profile</h1>
      {loaded === 'failed' ? (
        <p role="alert">Sorry, your profile cannot be shown at the moment. Try again later.</p>
      ) : (
        <>
          {!loaded && (
            <p>
              Tell us about yourself. Your CV is written only from what you give here, and jobs are
              matched to your skills.
            </p>
          )}
          <ProfileForm
            initialState={{ values: loaded ?? { ...emptyValues, name: user.name }, errors: {} }}
            action={saveProfile}
          />
        </>
      )}
    </>
  )
}

/** The saved profile's values, null before the first save, or failed. */
async function loadProfile(accessToken: string): Promise<ProfileValues | null | 'failed'> {
  try {
    const { data, response } = await apiClient(accessToken).GET('/profile')
    if (data) return valuesFrom(data)
    return response.status === 404 ? null : 'failed'
  } catch {
    return 'failed'
  }
}
