import { seal, unseal, sealSession, unsealSession, type Session } from './session'

const secret = 'a-session-secret-of-32-characters!'

const session: Session = {
  user: { username: 'candidate', name: 'Casey Candidate', roles: ['CANDIDATE'] },
  accessToken: 'access',
  refreshToken: 'refresh',
  expiresAt: 1_900_000_000,
}

describe('session', () => {
  it('reads back what it sealed', async () => {
    expect(await unsealSession(await sealSession(session, secret), secret)).toEqual(session)
  })

  it('cannot be read with another secret', async () => {
    const sealed = await seal({ a: 1 }, secret)

    expect(await unseal(sealed, 'another-secret-of-32-characters!!!')).toBeNull()
  })

  it('cannot be read once expired', async () => {
    const sealed = await seal({ a: 1 }, secret, { maxAgeSeconds: -10 })

    expect(await unseal(sealed, secret)).toBeNull()
  })

  it('reads nothing from no cookie, or a tampered one', async () => {
    expect(await unseal(undefined, secret)).toBeNull()
    expect(await unseal('not.a.sealed.value', secret)).toBeNull()
  })

  it('refuses a short secret', async () => {
    await expect(seal({}, 'short')).rejects.toThrow('at least 32 characters')
  })
})
