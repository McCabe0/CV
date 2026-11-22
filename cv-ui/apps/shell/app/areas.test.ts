import { areasFor } from './areas'

const user = (...roles: string[]) => ({ username: 'u', name: 'u', roles })

describe('areasFor', () => {
  it('gives a candidate their profile and their CV', () => {
    expect(areasFor(user('CANDIDATE'))).toEqual([
      { name: 'Your profile', href: '/profile' },
      { name: 'Your CV', href: '/cv' },
    ])
  })

  it('gives an account without a known role nothing', () => {
    expect(areasFor(user('SOMETHING_ELSE'))).toEqual([])
  })
})
