import { hasRole, type User } from '@cv/auth'

export type Area = { name: string; href: string }

// Each area is a zone. The admin zone joins this list in slice 6.
const AREAS: (Area & { roles: string[] })[] = [
  { name: 'Your profile', href: '/profile', roles: ['CANDIDATE'] },
  { name: 'Your CV', href: '/cv', roles: ['CANDIDATE'] },
  { name: 'Jobs for you', href: '/jobs', roles: ['CANDIDATE'] },
]

/** What the user's roles let them use. */
export const areasFor = (user: User): Area[] =>
  AREAS.filter(({ roles }) => hasRole(user, roles)).map(({ name, href }) => ({ name, href }))
