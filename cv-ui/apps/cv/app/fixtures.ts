import type { CvRequest } from './cvRequest'

/** CV requests as the api returns them, for the tests. */
export const cvRequest = (fields: Partial<CvRequest> = {}): CvRequest => ({
  id: 7,
  status: 'REQUESTED',
  questions: [],
  answers: [],
  rounds: [],
  ...fields,
})

export const draft = {
  summary: 'Backend engineer who cut settlement time by 40%.',
  skills: ['Kotlin', 'PostgreSQL'],
  roles: [
    {
      title: 'Software Engineer',
      company: 'Acme Payments',
      dates: '2021 – now',
      bullets: ['Cut settlement time by 40%'],
    },
  ],
  education: ['BSc Computer Science'],
  projects: ['A CV writer'],
  certifications: ['AWS Developer'],
  languages: ['English', 'Irish'],
}

export const round = (number: number, score: number, approved = false) => ({
  number,
  score,
  approved,
  issues: approved ? [] : [{ kind: 'NO_IMPACT' as const, detail: 'Add numbers to the bullets' }],
  summary: `Draft ${number}.`,
})
