const API_BASE_URL = 'http://localhost:8080'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  })

  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Request failed with status ${response.status}`)
  }

  return response.json() as Promise<T>
}

export type Profile = {
  name: string
  skills: string[]
  experience: string
  education: string
  targetRole?: string
  yearsOfExperience?: string
  location?: string
  workAuthorization?: string
  projects?: string[]
  certifications?: string[]
  languages?: string[]
}

export type CvResponse = {
  profileId: number
  cvId: number
  headline: string
  summary: string
  keySkills: string[]
  experienceBullets: string[]
  educationSection: string
  atsKeywords: string[]
}

export type JobItem = {
  id: string
  title: string
  company: string
  location: string
  description: string
  requiredSkills: string[]
  roleKeywords: string[]
  source: string
  url?: string
}

export type JobSearchCriteria = {
  skills: string[]
  location?: string
  roleKeywords: string[]
}

export type JobSearchResponse = {
  searchId: number
  savedJobIds: number[]
  jobs: JobItem[]
}

export type JobMatchPayload = {
  profileId?: number
  cvId?: number
  generatedCvOrProfile: string
  profileSkills: string[]
  jobs: JobItem[]
  includeReasoning?: boolean
  reasoningLimit?: number
}

export type JobMatchResult = {
  job: JobItem
  score: number
  skillOverlapPercent: number
  requiredSkillsMissing: string[]
  confidence: number
  reasoning: string
}

export type JobMatchResponse = {
  profileId?: number
  cvId?: number
  matchIds: number[]
  matches: JobMatchResult[]
}

export async function getHello() {
  return request<{ message: string }>('/hello')
}

export async function generateCv(profile: Profile): Promise<CvResponse> {
  const profileResponse = await request<{ profileId: number }>('/cv/profiles', {
    method: 'POST',
    body: JSON.stringify(profile),
  })

  return request<CvResponse>('/cv/generate', {
    method: 'POST',
    body: JSON.stringify({ profileId: profileResponse.profileId }),
  })
}

export async function searchJobs(criteria: JobSearchCriteria): Promise<JobSearchResponse> {
  return request<JobSearchResponse>('/jobs/search', {
    method: 'POST',
    body: JSON.stringify(criteria),
  })
}

export async function matchJobs(payload: JobMatchPayload): Promise<JobMatchResponse> {
  return request<JobMatchResponse>('/jobs/match', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function getRecommendations(profileId: number): Promise<JobMatchResponse> {
  return request<JobMatchResponse>(`/jobs/recommendations/${profileId}`)
}
