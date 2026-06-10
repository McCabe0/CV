const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

// Gemini-backed calls can be slow, so abort rather than letting a hung request spin forever.
const REQUEST_TIMEOUT_MS = 60_000

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {}),
      },
      signal: controller.signal,
    })

    if (!response.ok) {
      const message = await response.text()
      throw new Error(message || `Request failed with status ${response.status}`)
    }

    return response.json() as Promise<T>
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error(`Request timed out after ${REQUEST_TIMEOUT_MS / 1000}s`)
    }
    throw error
  } finally {
    clearTimeout(timeout)
  }
}

export type WorkExperience = {
  company: string
  title: string
  startDate?: string
  endDate?: string
  bullets: string[]
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
  email?: string
  phone?: string
  linkedin?: string
  portfolio?: string
  workHistory?: WorkExperience[]
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
  preferredLocation?: string
  candidateYears?: number
  minScore?: number
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

export async function generateCvForProfile(profileId: number): Promise<CvResponse> {
  return request<CvResponse>('/cv/generate', {
    method: 'POST',
    body: JSON.stringify({ profileId }),
  })
}

export async function generateCv(profile: Profile): Promise<CvResponse> {
  const profileResponse = await request<{ profileId: number }>('/cv/profiles', {
    method: 'POST',
    body: JSON.stringify(profile),
  })

  return generateCvForProfile(profileResponse.profileId)
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
