import type { NextResponse } from 'next/server'
import type { AuthConfig } from './config'
import { SESSION_COOKIE } from './session'

export const LOGIN_COOKIE = 'cv_login'
/** Long enough to sign in, no longer. */
export const LOGIN_MAX_AGE_SECONDS = 600

/** Http only, so no script can read it, and lax, so it comes back from keycloak's redirect. */
const cookie = (config: AuthConfig, maxAge?: number) => ({
  httpOnly: true,
  secure: config.secureCookies,
  sameSite: 'lax' as const,
  path: '/',
  ...(maxAge !== undefined && { maxAge }),
})

export const sessionCookie = (config: AuthConfig) => cookie(config)
export const loginCookie = (config: AuthConfig) => cookie(config, LOGIN_MAX_AGE_SECONDS)

export const clearSessionCookie = (response: NextResponse) =>
  response.cookies.set(SESSION_COOKIE, '', { path: '/', maxAge: 0 })
export const clearLoginCookie = (response: NextResponse) =>
  response.cookies.set(LOGIN_COOKIE, '', { path: '/', maxAge: 0 })
