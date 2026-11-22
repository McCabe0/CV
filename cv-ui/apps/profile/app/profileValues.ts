import type { components } from '@cv/api-client'

export type ProfileRequest = components['schemas']['ProfileRequest']
export type ProfileResponse = components['schemas']['ProfileResponse']

/** The form as typed: every field a string, each list one item a line (skills may also use commas). */
export type ProfileValues = {
  name: string
  targetRole: string
  yearsOfExperience: string
  location: string
  workAuthorization: string
  skills: string
  experience: string
  education: string
  projects: string
  certifications: string
  languages: string
  email: string
  phone: string
  linkedin: string
  portfolio: string
}

export type ProfileErrors = Partial<Record<keyof ProfileValues, string>>

export type ProfileFormState = {
  values: ProfileValues
  errors: ProfileErrors
  /** About the whole form, eg the api could not be reached. */
  formError?: string
  saved?: boolean
}

export const emptyValues: ProfileValues = {
  name: '',
  targetRole: '',
  yearsOfExperience: '',
  location: '',
  workAuthorization: '',
  skills: '',
  experience: '',
  education: '',
  projects: '',
  certifications: '',
  languages: '',
  email: '',
  phone: '',
  linkedin: '',
  portfolio: '',
}

const FIELDS = Object.keys(emptyValues) as (keyof ProfileValues)[]

export function readForm(data: FormData): ProfileValues {
  return Object.fromEntries(
    FIELDS.map((field) => [field, String(data.get(field) ?? '').trim()]),
  ) as ProfileValues
}

// The limits are the api's, in ProfileRequest, so the form never sends what the api would refuse.
type Text = {
  kind: 'text'
  max: number
  about: string
  required?: string
  pattern?: [RegExp, string]
}
type List = {
  kind: 'list'
  split: RegExp
  maxItems: number
  maxLength: number
  plural: string
  singular: string
  required?: string
}

const LINES = /\n/
const LINES_OR_COMMAS = /[\n,]/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const WEB_ADDRESS = /^https?:\/\/\S+$/

const RULES: Record<keyof ProfileValues, Text | List> = {
  name: { kind: 'text', max: 100, about: 'Your name', required: 'Enter your name' },
  targetRole: { kind: 'text', max: 100, about: 'The role you want' },
  yearsOfExperience: { kind: 'text', max: 20, about: 'Years of experience' },
  location: { kind: 'text', max: 100, about: 'Your location' },
  workAuthorization: { kind: 'text', max: 100, about: 'Your right to work' },
  skills: {
    kind: 'list',
    split: LINES_OR_COMMAS,
    maxItems: 50,
    maxLength: 50,
    plural: 'skills',
    singular: 'skill',
    required: 'Enter at least one skill',
  },
  experience: {
    kind: 'text',
    max: 5000,
    about: 'Your experience',
    required: 'Enter your experience',
  },
  education: { kind: 'text', max: 2000, about: 'Your education', required: 'Enter your education' },
  projects: {
    kind: 'list',
    split: LINES,
    maxItems: 20,
    maxLength: 500,
    plural: 'projects',
    singular: 'project',
  },
  certifications: {
    kind: 'list',
    split: LINES,
    maxItems: 20,
    maxLength: 200,
    plural: 'certifications',
    singular: 'certification',
  },
  languages: {
    kind: 'list',
    split: LINES,
    maxItems: 20,
    maxLength: 50,
    plural: 'languages',
    singular: 'language',
  },
  email: {
    kind: 'text',
    max: 254,
    about: 'Your email address',
    pattern: [EMAIL, 'Enter an email address like name@example.com'],
  },
  phone: { kind: 'text', max: 30, about: 'Your phone number' },
  linkedin: {
    kind: 'text',
    max: 300,
    about: 'Your LinkedIn address',
    pattern: [WEB_ADDRESS, 'Enter your LinkedIn address, starting https://'],
  },
  portfolio: {
    kind: 'text',
    max: 300,
    about: 'Your portfolio address',
    pattern: [WEB_ADDRESS, 'Enter your portfolio address, starting https://'],
  },
}

const items = (value: string, split: RegExp) =>
  value
    .split(split)
    .map((item) => item.trim())
    .filter(Boolean)

/** What is wrong with one field, if anything. */
function problem(value: string, rule: Text | List): string | undefined {
  if (rule.kind === 'list') {
    const list = items(value, rule.split)
    if (list.length === 0) return rule.required
    if (list.length > rule.maxItems) return `Enter ${rule.maxItems} ${rule.plural} or fewer`
    if (list.some((item) => item.length > rule.maxLength))
      return `Each ${rule.singular} must be ${rule.maxLength} characters or fewer`
    return undefined
  }
  if (!value) return rule.required
  if (value.length > rule.max) return `${rule.about} must be ${rule.max} characters or fewer`
  if (rule.pattern && !rule.pattern[0].test(value)) return rule.pattern[1]
  return undefined
}

export type Validation =
  { ok: true; request: ProfileRequest } | { ok: false; errors: ProfileErrors }

/** Every problem at once, or the request to send. */
export function validate(values: ProfileValues): Validation {
  const errors: ProfileErrors = {}
  for (const field of FIELDS) {
    const message = problem(values[field], RULES[field])
    if (message) errors[field] = message
  }
  if (Object.keys(errors).length > 0) return { ok: false, errors }

  const optional = (field: keyof ProfileValues) => (values[field] ? { [field]: values[field] } : {})
  return {
    ok: true,
    request: {
      name: values.name,
      skills: items(values.skills, LINES_OR_COMMAS),
      experience: values.experience,
      education: values.education,
      ...optional('targetRole'),
      ...optional('yearsOfExperience'),
      ...optional('location'),
      ...optional('workAuthorization'),
      projects: items(values.projects, LINES),
      certifications: items(values.certifications, LINES),
      languages: items(values.languages, LINES),
      ...optional('email'),
      ...optional('phone'),
      ...optional('linkedin'),
      ...optional('portfolio'),
      // The form has no work history yet; the api's default is this same empty list.
      workHistory: [],
    },
  }
}

/**
 * The form, filled from the saved profile. The spec marks none of its fields required (springdoc does
 * not read kotlin's nullability), so a missing one is taken as blank.
 */
export function valuesFrom(profile: ProfileResponse): ProfileValues {
  const text = (value: string | null | undefined) => value ?? ''
  const list = (value: string[] | undefined, separator: string) => (value ?? []).join(separator)
  return {
    name: text(profile.name),
    targetRole: text(profile.targetRole),
    yearsOfExperience: text(profile.yearsOfExperience),
    location: text(profile.location),
    workAuthorization: text(profile.workAuthorization),
    skills: list(profile.skills, ', '),
    experience: text(profile.experience),
    education: text(profile.education),
    projects: list(profile.projects, '\n'),
    certifications: list(profile.certifications, '\n'),
    languages: list(profile.languages, '\n'),
    email: text(profile.email),
    phone: text(profile.phone),
    linkedin: text(profile.linkedin),
    portfolio: text(profile.portfolio),
  }
}
