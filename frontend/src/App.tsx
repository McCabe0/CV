import React, { useEffect, useMemo, useState } from 'react'
import {
  generateCv,
  generateCvForProfile,
  getRecommendations,
  matchJobs,
  searchJobs,
  type CvResponse,
  type JobItem,
  type JobMatchPayload,
  type JobMatchResult,
  type Profile,
} from './api'
import CvPreview from './components/CvPreview'
import JobMatchList from './components/JobMatchList'
import ProfileForm from './components/ProfileForm'
import { downloadCvPdf } from './cvPdf'

type Step = 'profile' | 'cv' | 'results'

const STEPS: { key: Step; label: string }[] = [
  { key: 'profile', label: '1. Profile' },
  { key: 'cv', label: '2. CV' },
  { key: 'results', label: '3. Job Matches' },
]

function parseYears(value?: string): number | undefined {
  if (!value) return undefined
  const match = value.match(/\d+/)
  return match ? Number(match[0]) : undefined
}

function deriveFallbackKeywords(profile: Profile, cv: CvResponse): string[] {
  const fromTargetRole = profile.targetRole ? [profile.targetRole] : []
  const fromHeadline = cv.headline
    .split(' ')
    .map((word) => word.replace(/[^a-zA-Z]/g, '').trim())
    .filter((word) => word.length > 3)
    .slice(0, 3)

  return [...new Set([...fromTargetRole, ...fromHeadline])]
}

export default function App() {
  const [step, setStep] = useState<Step>('profile')
  const [profile, setProfile] = useState<Profile | null>(null)
  const [profileId, setProfileId] = useState<number | null>(null)
  const [cvId, setCvId] = useState<number | null>(null)
  const [cv, setCv] = useState<CvResponse | null>(null)
  const [matches, setMatches] = useState<JobMatchResult[]>([])
  const [lastMatchPayload, setLastMatchPayload] = useState<JobMatchPayload | null>(null)

  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [cvLoading, setCvLoading] = useState(false)
  const [cvError, setCvError] = useState<string | null>(null)
  const [resultsLoading, setResultsLoading] = useState(false)
  const [resultsError, setResultsError] = useState<string | null>(null)

  const stepIndex = useMemo(() => STEPS.findIndex((entry) => entry.key === step), [step])

  // Track the furthest step reached so the stepper can navigate back to visited steps.
  const [maxStepIndex, setMaxStepIndex] = useState(0)
  useEffect(() => {
    setMaxStepIndex((current) => Math.max(current, stepIndex))
  }, [stepIndex])

  const handleReset = () => {
    setProfile(null)
    setProfileId(null)
    setCvId(null)
    setCv(null)
    setMatches([])
    setLastMatchPayload(null)
    setProfileError(null)
    setCvError(null)
    setResultsError(null)
    setMaxStepIndex(0)
    setStep('profile')
  }

  const handleDownloadPdf = () => {
    if (cv) {
      downloadCvPdf(cv, profile)
    }
  }

  const handleRegenerateCv = async () => {
    if (!profileId) {
      setCvError('No profile ID found. Please complete Step 1 first.')
      return
    }

    setCvLoading(true)
    setCvError(null)

    try {
      const regenerated = await generateCvForProfile(profileId)
      setCv(regenerated)
      setCvId(regenerated.cvId)
    } catch (error) {
      setCvError(error instanceof Error ? error.message : 'Failed to regenerate CV')
    } finally {
      setCvLoading(false)
    }
  }

  const handleProfileSubmit = async (profilePayload: Profile) => {
    setProfileLoading(true)
    setProfileError(null)
    setCvError(null)

    try {
      const generatedCv = await generateCv(profilePayload)
      setProfile(profilePayload)
      setProfileId(generatedCv.profileId)
      setCvId(generatedCv.cvId)
      setCv(generatedCv)
      setStep('cv')
    } catch (error) {
      setProfileError(error instanceof Error ? error.message : 'Failed to create profile and generate CV')
    } finally {
      setProfileLoading(false)
    }
  }

  const createMatchPayload = (
    jobs: JobItem[],
    currentCv: CvResponse,
    currentProfile: Profile,
    criteria: { location?: string; minScore: number },
  ): JobMatchPayload => {
    const cvText = [currentCv.headline, currentCv.summary, ...currentCv.experienceBullets, currentCv.educationSection].join('\n')

    return {
      profileId: profileId ?? undefined,
      cvId: cvId ?? undefined,
      generatedCvOrProfile: cvText,
      profileSkills: currentCv.keySkills.length > 0 ? currentCv.keySkills : currentProfile.skills,
      jobs,
      includeReasoning: false,
      reasoningLimit: 3,
      preferredLocation: criteria.location,
      candidateYears: parseYears(currentProfile.yearsOfExperience),
      minScore: criteria.minScore,
    }
  }

  const handleFindMatches = async (criteria: {
    roleKeywords: string[]
    location?: string
    includeReasoning: boolean
    reasoningLimit: number
    minScore: number
  }) => {
    if (!cv || !profile) {
      setCvError('CV and profile data are required before matching jobs.')
      return
    }

    setResultsLoading(true)
    setResultsError(null)
    setMatches([])

    try {
      const fallbackKeywords = deriveFallbackKeywords(profile, cv)

      let searchResponse = await searchJobs({
        skills: cv.keySkills.length > 0 ? cv.keySkills : profile.skills,
        location: criteria.location,
        roleKeywords: criteria.roleKeywords.length > 0 ? criteria.roleKeywords : fallbackKeywords,
      })

      if (searchResponse.jobs.length === 0) {
        searchResponse = await searchJobs({
          skills: profile.skills.slice(0, 8),
          roleKeywords: fallbackKeywords,
          location: undefined,
        })
      }

      if (searchResponse.jobs.length > 0) {
        const payload = createMatchPayload(searchResponse.jobs, cv, profile, {
          location: criteria.location,
          minScore: criteria.minScore,
        })
        const matchResponse = await matchJobs({
          ...payload,
          includeReasoning: criteria.includeReasoning,
          reasoningLimit: criteria.reasoningLimit,
        })

        setLastMatchPayload(payload)
        setMatches(matchResponse.matches)
        setStep('results')
        return
      }

      if (profileId) {
        const recommendationResponse = await getRecommendations(profileId)
        setMatches(recommendationResponse.matches)
        setResultsError(
          recommendationResponse.matches.length === 0
            ? 'No direct search hits. We also tried recommendations but found no matches yet.'
            : 'No direct search hits. Showing recommended matches instead.',
        )
      }

      setStep('results')
    } catch (error) {
      setResultsError(error instanceof Error ? error.message : 'Failed to find job matches')
      setStep('results')
    } finally {
      setResultsLoading(false)
    }
  }

  const handleGenerateReasoning = async () => {
    if (!lastMatchPayload) {
      setResultsError('No match context available. Run job matching first.')
      return
    }

    setResultsLoading(true)
    setResultsError(null)

    try {
      const response = await matchJobs({
        ...lastMatchPayload,
        includeReasoning: true,
        reasoningLimit: 3,
      })
      setMatches(response.matches)
    } catch (error) {
      setResultsError(error instanceof Error ? error.message : 'Failed to generate reasoning')
    } finally {
      setResultsLoading(false)
    }
  }

  const handleRefreshRecommendations = async () => {
    if (!profileId) {
      setResultsError('No profile ID found. Please complete Step 1 first.')
      return
    }

    setResultsLoading(true)
    setResultsError(null)

    try {
      const recommendationResponse = await getRecommendations(profileId)
      setMatches(recommendationResponse.matches)
    } catch (error) {
      setResultsError(error instanceof Error ? error.message : 'Failed to load recommendations')
    } finally {
      setResultsLoading(false)
    }
  }

  return (
    <div className="app-shell">
      <div className="card">
        <div className="card-header">
          <div>
            <h1>Skill2Career</h1>
            <p className="muted">
              Build your profile, generate your CV, and get matched jobs in one flow.
            </p>
          </div>
          <button type="button" onClick={handleReset} disabled={stepIndex === 0 && !profile}>
            Start over
          </button>
        </div>

        <div className="stepper">
          {STEPS.map((entry, index) => (
            <button
              key={entry.key}
              type="button"
              className={`step-pill ${stepIndex >= index ? 'active' : ''}`}
              onClick={() => setStep(entry.key)}
              disabled={index > maxStepIndex}
            >
              {entry.label}
            </button>
          ))}
        </div>

        <p className="muted id-line">
          Profile ID: <strong>{profileId ?? 'N/A'}</strong> | CV ID: <strong>{cvId ?? 'N/A'}</strong>
        </p>
      </div>

      {step === 'profile' ? (
        <ProfileForm initialValue={profile ?? undefined} loading={profileLoading} error={profileError} onSubmit={handleProfileSubmit} />
      ) : null}

      {step === 'cv' ? (
        <CvPreview
          cv={cv}
          loading={cvLoading}
          error={cvError}
          onUpdateCv={setCv}
          onContinue={handleFindMatches}
          continueLoading={resultsLoading}
          onBackToProfile={() => setStep('profile')}
          onRegenerate={handleRegenerateCv}
          onDownloadPdf={handleDownloadPdf}
        />
      ) : null}

      {step === 'results' ? (
        <div className="grid">
          <div className="results-actions">
            <button type="button" onClick={handleRefreshRecommendations} disabled={resultsLoading || !profileId}>
              {resultsLoading ? 'Refreshing...' : 'Refresh recommendations'}
            </button>
            <button type="button" onClick={handleGenerateReasoning} disabled={resultsLoading || !lastMatchPayload}>
              {resultsLoading ? 'Generating...' : 'Generate reasoning for top 3'}
            </button>
          </div>

          <JobMatchList
            matches={matches}
            loading={resultsLoading}
            error={resultsError}
            onBackToCv={() => setStep('cv')}
          />
        </div>
      ) : null}
    </div>
  )
}
