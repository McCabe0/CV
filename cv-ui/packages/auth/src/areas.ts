import { hasRole } from './roles'
import type { User } from './session'

export type Area = { name: string; href: string }

// Each area is a zone, shown to the roles that may use it.
const AREAS: (Area & { roles: string[] })[] = [
  { name: 'Your profile', href: '/profile', roles: ['CANDIDATE'] },
  { name: 'Your CV', href: '/cv', roles: ['CANDIDATE'] },
  { name: 'Jobs for you', href: '/jobs', roles: ['CANDIDATE'] },
  { name: 'Admin', href: '/admin', roles: ['ADMIN'] },
]

/** What the user's roles let them use. */
export const areasFor = (user: User): Area[] =>
  AREAS.filter(({ roles }) => hasRole(user, roles)).map(({ name, href }) => ({ name, href }))
