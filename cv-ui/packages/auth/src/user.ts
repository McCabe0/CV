import { decodeJwt } from 'jose'
import type { User } from './session'

// Roles keycloak gives every user. They mean nothing here.
const keycloakDefault = (role: string) =>
  role.startsWith('default-roles-') || role === 'offline_access' || role === 'uma_authorization'

type Claims = { preferred_username: string; name?: string; roles?: string[] }

/**
 * The user, from an access token straight from keycloak's token endpoint, so its signature is not
 * checked here. The api checks it on every request.
 */
export function userFromAccessToken(accessToken: string): User {
  const claims = decodeJwt<Claims>(accessToken)
  return {
    username: claims.preferred_username,
    name: claims.name ?? claims.preferred_username,
    roles: (claims.roles ?? []).filter((role) => !keycloakDefault(role)),
  }
}

export { hasRole } from './roles'
