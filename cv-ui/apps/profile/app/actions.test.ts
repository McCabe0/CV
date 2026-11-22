import { apiClient } from '@cv/api-client'
import { requireSession } from '@cv/auth/server'
import { saveProfile } from './actions'
import { emptyValues } from './profileValues'

jest.mock('@cv/auth/server', () => ({ requireSession: jest.fn() }))
jest.mock('@cv/api-client', () => ({ apiClient: jest.fn() }))

const PUT = jest.fn()
const initial = { values: emptyValues, errors: {} }

const form = (fields: Record<string, string>) => {
  const data = new FormData()
  for (const [name, value] of Object.entries(fields)) data.set(name, value)
  return data
}

const validForm = () =>
  form({
    name: 'Casey',
    skills: 'kotlin, SQL',
    experience: 'Five years',
    education: 'BSc',
    email: 'casey@example.com',
  })

const saved = {
  id: 1,
  name: 'Casey',
  skills: ['kotlin', 'SQL'],
  experience: 'Five years',
  education: 'BSc',
  projects: [],
  certifications: [],
  languages: [],
  email: 'casey@example.com',
  workHistory: [],
}

const SORRY = 'Sorry, your profile could not be saved. Try again later.'

beforeEach(() => {
  jest.mocked(requireSession).mockResolvedValue({
    user: { username: 'casey', name: 'Casey', roles: ['CANDIDATE'] },
    accessToken: 'access',
    refreshToken: 'refresh',
    expiresAt: 0,
  })
  jest.mocked(apiClient).mockReturnValue({ PUT } as unknown as ReturnType<typeof apiClient>)
})

describe('saveProfile', () => {
  it('saves the profile as the signed in user, and shows what was saved', async () => {
    PUT.mockResolvedValue({ data: saved, response: { status: 200 } })

    const state = await saveProfile(initial, validForm())

    expect(requireSession).toHaveBeenCalledWith('/profile')
    expect(apiClient).toHaveBeenCalledWith('access')
    expect(PUT).toHaveBeenCalledWith('/profile', {
      body: expect.objectContaining({
        name: 'Casey',
        skills: ['kotlin', 'SQL'],
        email: 'casey@example.com',
      }),
    })
    expect(state).toEqual({
      values: expect.objectContaining({ name: 'Casey', skills: 'kotlin, SQL' }),
      errors: {},
      saved: true,
    })
  })

  it('returns the errors and what was entered, without calling the api, when the form is not valid', async () => {
    const data = validForm()
    data.set('name', '')

    const state = await saveProfile(initial, data)

    expect(PUT).not.toHaveBeenCalled()
    expect(state.errors).toEqual({ name: 'Enter your name' })
    expect(state.values.skills).toBe('kotlin, SQL')
    expect(state.saved).toBeUndefined()
  })

  it('says the api did not accept it when it refuses the request', async () => {
    PUT.mockResolvedValue({ data: undefined, error: { status: 400 }, response: { status: 400 } })

    const state = await saveProfile(initial, validForm())

    expect(state.formError).toBe('Your profile was not accepted. Check your answers and try again.')
    expect(state.values.name).toBe('Casey')
  })

  it('says sorry when the api fails', async () => {
    PUT.mockResolvedValue({ data: undefined, error: {}, response: { status: 500 } })

    expect((await saveProfile(initial, validForm())).formError).toBe(SORRY)
  })

  it('says sorry when the api cannot be reached', async () => {
    PUT.mockRejectedValue(new TypeError('fetch failed'))

    expect((await saveProfile(initial, validForm())).formError).toBe(SORRY)
  })
})
