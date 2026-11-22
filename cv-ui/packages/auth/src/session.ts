import { EncryptJWT, jwtDecrypt } from 'jose'

export type User = {
  username: string
  name: string
  roles: string[]
}

/** Holds keycloak's tokens, so it only ever lives sealed, in an http only cookie, on the server. */
export type Session = {
  user: User
  accessToken: string
  refreshToken: string
  /** When the access token expires, in seconds since the epoch. */
  expiresAt: number
}

export const SESSION_COOKIE = 'cv_session'

// Keycloak's default sso session lifespan, 10 hours.
const DEFAULT_MAX_AGE_SECONDS = 36_000

const keyFrom = async (secret: string) => {
  if (secret.length < 32) throw new Error('SESSION_SECRET must be at least 32 characters')
  return new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(secret)))
}

/** Encrypts a value so only this server, with this secret, can read it, and only until it expires. */
export async function seal(
  value: unknown,
  secret: string,
  { maxAgeSeconds = DEFAULT_MAX_AGE_SECONDS } = {},
): Promise<string> {
  return new EncryptJWT({ value })
    .setProtectedHeader({ alg: 'dir', enc: 'A256GCM' })
    .setIssuedAt()
    .setExpirationTime(Math.floor(Date.now() / 1000) + maxAgeSeconds)
    .encrypt(await keyFrom(secret))
}

/** Null for anything that is not a valid, unexpired value sealed with this secret. */
export async function unseal<T>(sealed: string | undefined, secret: string): Promise<T | null> {
  if (!sealed) return null
  try {
    const { payload } = await jwtDecrypt<{ value: T }>(sealed, await keyFrom(secret))
    return payload.value
  } catch {
    return null
  }
}

export const sealSession = (session: Session, secret: string) => seal(session, secret)

export const unsealSession = (sealed: string | undefined, secret: string) =>
  unseal<Session>(sealed, secret)
