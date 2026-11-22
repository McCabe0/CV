import { cookies } from 'next/headers'
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
