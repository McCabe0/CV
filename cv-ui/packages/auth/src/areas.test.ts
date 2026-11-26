import { areasFor } from './areas'

const user = (...roles: string[]) => ({ username: 'u', name: 'u', roles })

describe('areasFor', () => {
  it('gives a candidate their profile, their CV, and jobs that fit them', () => {
    expect(areasFor(user('CANDIDATE'))).toEqual([
      { name: 'Your profile', href: '/profile' },
      { name: 'Your CV', href: '/cv' },
      { name: 'Jobs for you', href: '/jobs' },
    ])
  })

  it('gives an admin the companies, and only an admin', () => {
    expect(areasFor(user('ADMIN'))).toEqual([{ name: 'Admin', href: '/admin' }])
    expect(areasFor(user('CANDIDATE', 'ADMIN')).map(({ href }) => href)).toEqual([
      '/profile',
      '/cv',
      '/jobs',
      '/admin',
    ])
  })

  it('gives an account without a known role nothing', () => {
    expect(areasFor(user('SOMETHING_ELSE'))).toEqual([])
  })
})
