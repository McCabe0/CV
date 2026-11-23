import type { components } from '@cv/api-client'

export type JobMatch = components['schemas']['JobMatchResponse']
export type FitSummary = components['schemas']['FitSummaryResponse']

/** The longest place the api takes, in characters. */
export const LOCATION_MAX_LENGTH = 100

export type Filter = { remoteOnly: boolean; location: string }

type SearchParams = Record<string, string | string[] | undefined>

const first = (value: string | string[] | undefined) => (Array.isArray(value) ? value[0] : value)

/**
 * The filter, from the page's url: a plain get form fills it, so it works without javascript, and a
 * search can be shared as a link. A place too long for the api is cut, rather than failing the page.
 */
export function readFilter(params: SearchParams): Filter {
  return {
    remoteOnly: first(params.remoteOnly) === 'on',
    location: (first(params.location) ?? '').trim().slice(0, LOCATION_MAX_LENGTH),
  }
}

const WORKPLACES: Record<string, string> = { REMOTE: 'Remote', HYBRID: 'Hybrid', ONSITE: 'On site' }

/** Where the job is done, in plain words; nothing when its board did not say. */
export const workplaceLabel = (workplace: string | null | undefined) =>
  workplace ? WORKPLACES[workplace] : undefined

/** The candidate's skills a job asks for, as a sentence. */
export function skillsLine(skills: string[]): string {
  const listed =
    skills.length > 1 ? `${skills.slice(0, -1).join(', ')} and ${skills.at(-1)}` : skills[0]
  return `Asks for ${listed}`
}
