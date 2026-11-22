import { cookies, headers } from 'next/headers'
import { redirect } from 'next/navigation'
import { authConfig, type AuthConfig } from './config'
import { SESSION_COOKIE, unsealSession, type Session } from './session'

/** For server components and route handlers. The session never reaches the browser. */
export async function getSession(config?: AuthConfig): Promise<Session | null> {
  // Cookies first: that is what makes next render the page per request, not once at build time,
  // when there are no settings. So the settings are only read after.
  const sealed = (await cookies()).get(SESSION_COOKIE)?.value
  return unsealSession(sealed, (config ?? authConfig()).sessionSecret)
}

/** The session, or a redirect to sign in that returns the user to `returnTo` afterwards. */
export async function requireSession(returnTo: string, config?: AuthConfig): Promise<Session> {
  const session = await getSession(config)
  if (!session) {
    // The sign in routes live in the shell, so this is absolute rather than under a zone's base path.
    const login = new URL('/auth/login', (config ?? authConfig()).appUrl)
    redirect(`${login.href}?returnTo=${encodeURIComponent(returnTo)}`)
  }
  return session
}

/**
 * Redirects from a server action to a page in this zone, eg `redirectInZone('/cv', '/requests/7')`.
 *
 * Next adds the zone's base path to an action's redirect when javascript sent the form, but not when the
 * browser posted it without javascript, so the user would land outside the zone. Next marks a javascript
 * call with the `next-action` header, so the base path is only added without it.
 */
export async function redirectInZone(basePath: string, path: string): Promise<never> {
  const sentByJavascript = (await headers()).has('next-action')
  redirect(sentByJavascript ? path : `${basePath}${path}`)
}
