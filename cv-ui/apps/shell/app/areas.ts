import { hasRole, type User } from '@cv/auth'

export type Area = { name: string; href: string }

// Each area is a zone. The cv, jobs and admin zones join this list in later slices.
const AREAS: (Area & { roles: string[] })[] = [
  { name: 'Your profile', href: '/profile', roles: ['CANDIDATE'] },
]

/** What the user's roles let them use. */
export const areasFor = (user: User): Area[] =>
  AREAS.filter(({ roles }) => hasRole(user, roles)).map(({ name, href }) => ({ name, href }))
