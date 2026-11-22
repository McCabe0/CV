import { NextResponse, type NextRequest } from 'next/server'
import { authConfig, type AuthConfig } from './config'
import { errorMessage } from './errors'
import {
  clearLoginCookie,
  clearSessionCookie,
  LOGIN_COOKIE,
  LOGIN_MAX_AGE_SECONDS,
  loginCookie,
  sessionCookie,
} from './cookies'
import { beginLogin, callbackUrl, completeLogin, endSession, type LoginState } from './oidc'
import { seal, sealSession, SESSION_COOKIE, unseal, unsealSession } from './session'

type PendingLogin = LoginState & { returnTo: string }

/** Only a path on this site, so sign in can never be used to send someone elsewhere. */
const safeReturnTo = (value: string | null) =>
  value?.startsWith('/') && !value.startsWith('//') && !value.startsWith('/\\') ? value : '/'

/** GET /auth/login?returnTo=/path */
export async function login(request: NextRequest, config: AuthConfig = authConfig()) {
  const returnTo = safeReturnTo(request.nextUrl.searchParams.get('returnTo'))
  const { url, verifier, state } = await beginLogin(config)

  const response = NextResponse.redirect(url)
  const pending: PendingLogin = { verifier, state, returnTo }
  response.cookies.set(
    LOGIN_COOKIE,
    await seal(pending, config.sessionSecret, { maxAgeSeconds: LOGIN_MAX_AGE_SECONDS }),
    loginCookie(config),
  )
  return response
}

/** GET /auth/callback, where keycloak returns the user after they sign in. */
export async function callback(request: NextRequest, config: AuthConfig = authConfig()) {
  const failed = () => {
    const response = NextResponse.redirect(new URL('/auth/error', config.appUrl))
    clearLoginCookie(response)
    return response
  }

  const pending = await unseal<PendingLogin>(
    request.cookies.get(LOGIN_COOKIE)?.value,
    config.sessionSecret,
  )
  if (!pending) return failed()

  try {
    // The server may listen on another address, but keycloak redirected to the public url.
    const callback = new URL(`${callbackUrl(config).href}${request.nextUrl.search}`)
    const session = await completeLogin(config, callback, pending)

    const response = NextResponse.redirect(new URL(pending.returnTo, config.appUrl))
    response.cookies.set(
      SESSION_COOKIE,
      await sealSession(session, config.sessionSecret),
      sessionCookie(config),
    )
    clearLoginCookie(response)
    return response
  } catch (error) {
    console.error('Sign in failed:', errorMessage(error))
    return failed()
  }
}

/**
 * Next checks this for server actions, but not for route handlers. Same site cookies do not cover
 * it: they keep the session off a post from another site, but the browser would still accept the
 * response clearing it. A browser always sends an origin on a post.
 */
const fromAnotherSite = (request: NextRequest, config: AuthConfig) => {
  const origin = request.headers.get('origin')
  return origin !== null && origin !== new URL(config.appUrl).origin
}

/** POST /auth/logout */
export async function logout(request: NextRequest, config: AuthConfig = authConfig()) {
  if (fromAnotherSite(request, config)) return new NextResponse(null, { status: 403 })

  const session = await unsealSession(
    request.cookies.get(SESSION_COOKIE)?.value,
    config.sessionSecret,
  )
  if (session) {
    // Signing out of this service must not depend on keycloak being reachable.
    await endSession(config, session).catch((error: unknown) =>
      console.error('Keycloak logout failed:', errorMessage(error)),
    )
  }

  const response = NextResponse.redirect(new URL('/', config.appUrl), 303)
  clearSessionCookie(response)
  return response
}
