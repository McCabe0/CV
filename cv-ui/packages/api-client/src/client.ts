import createClient from 'openapi-fetch'
import type { paths } from './schema'

type Options = {
  /** For tests. */
  fetch?: (request: Request) => Promise<Response>
}

/**
 * A typed client for the api, generated from its openapi spec (`pnpm api:spec` against a running api).
 * Server side only: it carries the user's access token, which never reaches the browser.
 */
export function apiClient(accessToken: string, { fetch }: Options = {}) {
  const baseUrl = process.env.API_URL
  if (!baseUrl) throw new Error('Missing API_URL')

  return createClient<paths>({
    baseUrl,
    headers: { Authorization: `Bearer ${accessToken}` },
    ...(fetch && { fetch }),
  })
}

export type ApiClient = ReturnType<typeof apiClient>
