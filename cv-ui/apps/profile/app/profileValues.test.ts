import { emptyValues, readForm, validate, valuesFrom, type ProfileValues } from './profileValues'

const valid: ProfileValues = {
  ...emptyValues,
  name: 'Casey Candidate',
  skills: 'Kotlin, SQL',
  experience: 'Five years building APIs.',
  education: 'BSc Computer Science',
}

const errorsFor = (values: Partial<ProfileValues>) => {
  const result = validate({ ...valid, ...values })
  return result.ok ? {} : result.errors
}

describe('readForm', () => {
  it('reads every field, trimming what was typed, and blank for one left out', () => {
    const data = new FormData()
    data.set('name', '  Casey  ')
    data.set('skills', 'Kotlin')

    expect(readForm(data)).toEqual({ ...emptyValues, name: 'Casey', skills: 'Kotlin' })
  })
})

describe('validate', () => {
  it('makes the request from a valid form, leaving out what was not given', () => {
    expect(validate(valid)).toEqual({
      ok: true,
      request: {
        name: 'Casey Candidate',
        skills: ['Kotlin', 'SQL'],
        experience: 'Five years building APIs.',
        education: 'BSc Computer Science',
        projects: [],
        certifications: [],
        languages: [],
        workHistory: [],
      },
    })
  })

  it('takes skills by comma or line, and the other lists by line, as a project can have commas', () => {
    const result = validate({
      ...valid,
      skills: 'Kotlin,\nSQL\n\n Docker ',
      projects: 'Built a CV writer, with agents\nA job matcher',
      certifications: 'AWS Developer',
      languages: 'English\nIrish',
    })

    expect(result.ok && result.request).toMatchObject({
      skills: ['Kotlin', 'SQL', 'Docker'],
      projects: ['Built a CV writer, with agents', 'A job matcher'],
      certifications: ['AWS Developer'],
      languages: ['English', 'Irish'],
    })
  })

  it('sends the optional details that were given', () => {
    const result = validate({
      ...valid,
      targetRole: 'Backend engineer',
      yearsOfExperience: '5',
      location: 'Dublin',
      workAuthorization: 'EU citizen',
      email: 'casey@example.com',
      phone: '+353 1 234 5678',
      linkedin: 'https://linkedin.com/in/casey',
      portfolio: 'https://casey.dev',
    })

    expect(result.ok && result.request).toMatchObject({
      targetRole: 'Backend engineer',
      yearsOfExperience: '5',
      location: 'Dublin',
      workAuthorization: 'EU citizen',
      email: 'casey@example.com',
      phone: '+353 1 234 5678',
      linkedin: 'https://linkedin.com/in/casey',
      portfolio: 'https://casey.dev',
    })
  })

  it.each([
    ['name', '', 'Enter your name'],
    ['skills', ' , ', 'Enter at least one skill'],
    ['experience', '', 'Enter your experience'],
    ['education', '', 'Enter your education'],
  ] as const)('needs %s', (field, value, message) => {
    expect(errorsFor({ [field]: value })).toEqual({ [field]: message })
  })

  it.each([
    ['name', 101, 'Your name must be 100 characters or fewer'],
    ['experience', 5001, 'Your experience must be 5000 characters or fewer'],
    ['education', 2001, 'Your education must be 2000 characters or fewer'],
    ['targetRole', 101, 'The role you want must be 100 characters or fewer'],
    ['yearsOfExperience', 21, 'Years of experience must be 20 characters or fewer'],
    ['location', 101, 'Your location must be 100 characters or fewer'],
    ['workAuthorization', 101, 'Your right to work must be 100 characters or fewer'],
    ['phone', 31, 'Your phone number must be 30 characters or fewer'],
  ] as const)('limits %s to what the api accepts', (field, length, message) => {
    expect(errorsFor({ [field]: 'a'.repeat(length) })).toEqual({ [field]: message })
  })

  it.each([
    [
      'skills',
      Array.from({ length: 51 }, (_, i) => `skill${i}`).join(','),
      'Enter 50 skills or fewer',
    ],
    ['skills', 'a'.repeat(51), 'Each skill must be 50 characters or fewer'],
    [
      'projects',
      Array.from({ length: 21 }, (_, i) => `p${i}`).join('\n'),
      'Enter 20 projects or fewer',
    ],
    ['projects', 'a'.repeat(501), 'Each project must be 500 characters or fewer'],
    [
      'certifications',
      Array.from({ length: 21 }, (_, i) => `c${i}`).join('\n'),
      'Enter 20 certifications or fewer',
    ],
    ['certifications', 'a'.repeat(201), 'Each certification must be 200 characters or fewer'],
    [
      'languages',
      Array.from({ length: 21 }, (_, i) => `l${i}`).join('\n'),
      'Enter 20 languages or fewer',
    ],
    ['languages', 'a'.repeat(51), 'Each language must be 50 characters or fewer'],
  ] as const)('limits the %s list to what the api accepts', (field, value, message) => {
    expect(errorsFor({ [field]: value })).toEqual({ [field]: message })
  })

  it.each(['casey', 'casey@', '@example.com', 'casey example@example.com'])(
    'needs a real email address: %s',
    (email) => {
      expect(errorsFor({ email })).toEqual({
        email: 'Enter an email address like name@example.com',
      })
    },
  )

  it.each([
    ['linkedin', 'linkedin.com/in/casey', 'Enter your LinkedIn address, starting https://'],
    ['portfolio', 'ftp://casey.dev', 'Enter your portfolio address, starting https://'],
    ['portfolio', 'https://casey .dev', 'Enter your portfolio address, starting https://'],
  ] as const)('needs a web address for %s: %s', (field, value, message) => {
    expect(errorsFor({ [field]: value })).toEqual({ [field]: message })
  })

  it('reports every problem at once', () => {
    expect(Object.keys(errorsFor({ name: '', education: '', email: 'x' }))).toEqual([
      'name',
      'education',
      'email',
    ])
  })
})

describe('valuesFrom', () => {
  it('fills the form from a saved profile', () => {
    expect(
      valuesFrom({
        id: 1,
        name: 'Casey',
        skills: ['Kotlin', 'SQL'],
        experience: 'Lots',
        education: 'Some',
        targetRole: 'Engineer',
        projects: ['One', 'Two'],
        certifications: [],
        languages: ['English'],
        email: 'casey@example.com',
        workHistory: [],
      }),
    ).toEqual({
      ...emptyValues,
      name: 'Casey',
      skills: 'Kotlin, SQL',
      experience: 'Lots',
      education: 'Some',
      targetRole: 'Engineer',
      projects: 'One\nTwo',
      languages: 'English',
      email: 'casey@example.com',
    })
  })
})
