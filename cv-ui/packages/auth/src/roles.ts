// No token handling here: the browser safe index exports this.
import type { User } from './session'

export const hasRole = (user: User, roles: readonly string[]) =>
  roles.some((role) => user.roles.includes(role))
