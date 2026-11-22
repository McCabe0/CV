import { hasRole, userFromAccessToken } from './user'

const token = (claims: object) =>
  `${btoa(JSON.stringify({ alg: 'none' }))}.${btoa(JSON.stringify(claims))}.signature`

describe('userFromAccessToken', () => {
  it('reads the user and their roles, leaving out keycloak defaults', () => {
    const user = userFromAccessToken(
      token({
        preferred_username: 'admin',
        name: 'Ada Admin',
        roles: ['CANDIDATE', 'ADMIN', 'offline_access', 'uma_authorization', 'default-roles-x'],
      }),
    )

    expect(user).toEqual({ username: 'admin', name: 'Ada Admin', roles: ['CANDIDATE', 'ADMIN'] })
  })

  it('names the user by their username when the token has no name, and no roles', () => {
    expect(userFromAccessToken(token({ preferred_username: 'casey' }))).toEqual({
      username: 'casey',
      name: 'casey',
      roles: [],
    })
  })
})

describe('hasRole', () => {
  it('says whether the user has any of the roles', () => {
    const user = { username: 'u', name: 'u', roles: ['CANDIDATE'] }

    expect(hasRole(user, ['ADMIN', 'CANDIDATE'])).toBe(true)
    expect(hasRole(user, ['ADMIN'])).toBe(false)
  })
})
