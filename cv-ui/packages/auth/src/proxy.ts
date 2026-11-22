import { NextResponse, type NextRequest } from 'next/server'
import { authConfig, type AuthConfig } from './config'
import { clearSessionCookie, sessionCookie } from './cookies'
import { errorMessage } from './errors'
import { contentSecurityPolicy } from './headers'
import { refreshSession } from './oidc'
import { sealSession, SESSION_COOKIE, unsealSession } from './session'
import { hasRole } from './roles'

// Refresh a little early, so a token never expires between here and the api.
const REFRESH_WITHIN_SECONDS = 60

type Options = {
  /** Send a signed out user to sign in, and back to this page afterwards. */
  requireSignIn?: boolean
  /** Also refuse a user without one of these roles: they see the zone's /forbidden page, with a 403. */
  roles?: readonly string[]
}

/**
 * Sets a content security policy with a new nonce on the request, where next finds it and adds the
 * nonce to its scripts, and on the response. `handle` must pass the request's headers on.
 */
async function withContentSecurityPolicy(
  request: NextRequest,
  handle: () => Promise<NextResponse>,
  formActions: string[],
) {
  const policy = contentSecurityPolicy(
    btoa(crypto.randomUUID()),
    formActions,
    process.env.NODE_ENV === 'development',
  )
  request.headers.set('content-security-policy', policy)
  const response = await handle()
  response.headers.set('content-security-policy', policy)
  return response
}

/**
 * Keeps the session's access token fresh, and sets the content security policy. Runs in each app's
 * proxy, before any page, because a page cannot set cookies. The refreshed session is passed on to
 * the page and back to the browser.
 */
export function sessionProxy(
  request: NextRequest,
  config: AuthConfig = authConfig(),
  options: Options = {},
) {
  // Signing out posts to the shell, which redirects to keycloak.
  return withContentSecurityPolicy(request, () => keepSessionFresh(request, config, options), [
    config.issuer.origin,
  ])
}

async function keepSessionFresh(
  request: NextRequest,
  config: AuthConfig,
  { requireSignIn = false, roles }: Options,
) {
  const { basePath, pathname, search } = request.nextUrl
  const next = () => NextResponse.next({ request: { headers: request.headers } })

  const signIn = () => {
    // The sign in routes live in the shell, so the return path must include this zone's base path.
    const login = new URL('/auth/login', config.appUrl)
    const path = basePath && pathname === '/' ? basePath : `${basePath}${pathname}`
    login.searchParams.set('returnTo', `${path}${search}`)
    return NextResponse.redirect(login)
  }

  const signedOut = () => {
    request.cookies.delete(SESSION_COOKIE)
    const response = requireSignIn ? signIn() : next()
    clearSessionCookie(response)
    return response
  }

  const sealed = request.cookies.get(SESSION_COOKIE)?.value
  if (!sealed) return requireSignIn ? signIn() : next()

  let session = await unsealSession(sealed, config.sessionSecret)
  if (!session) return signedOut()

  let refreshed: string | undefined
  if (session.expiresAt - Math.floor(Date.now() / 1000) <= REFRESH_WITHIN_SECONDS) {
    try {
      session = await refreshSession(config, session)
      refreshed = await sealSession(session, config.sessionSecret)
      request.cookies.set(SESSION_COOKIE, refreshed)
    } catch (error) {
      console.error('Session refresh failed:', errorMessage(error))
      return signedOut()
    }
  }

  const response =
    roles !== undefined && !hasRole(session.user, roles)
      ? NextResponse.rewrite(new URL(`${basePath}/forbidden`, request.url), {
          status: 403,
          request: { headers: request.headers },
        })
      : next()
  if (refreshed) response.cookies.set(SESSION_COOKIE, refreshed, sessionCookie(config))
  return response
}
