import React, { useMemo, useState } from 'react'
import {
  generateCv,
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

type Step = 'profile' | 'cv' | 'results'

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
  const [cvLoading] = useState(false)
  const [cvError, setCvError] = useState<string | null>(null)
  const [resultsLoading, setResultsLoading] = useState(false)
  const [resultsError, setResultsError] = useState<string | null>(null)

  const stepIndex = useMemo(() => (step === 'profile' ? 0 : step === 'cv' ? 1 : 2), [step])

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

  const createMatchPayload = (jobs: JobItem[], currentCv: CvResponse, currentProfile: Profile): JobMatchPayload => {
    const cvText = [currentCv.headline, currentCv.summary, ...currentCv.experienceBullets, currentCv.educationSection].join('\n')

    return {
      profileId: profileId ?? undefined,
      cvId: cvId ?? undefined,
      generatedCvOrProfile: cvText,
      profileSkills: currentCv.keySkills.length > 0 ? currentCv.keySkills : currentProfile.skills,
      jobs,
      includeReasoning: false,
      reasoningLimit: 3,
    }
  }

  const handleFindMatches = async (criteria: {
    roleKeywords: string[]
    location?: string
    includeReasoning: boolean
    reasoningLimit: number
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
        const payload = createMatchPayload(searchResponse.jobs, cv, profile)
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
        <h1 style={{ margin: '0 0 4px' }}>Skill2Career</h1>
        <p className="muted" style={{ margin: 0 }}>
          Build your profile, generate your CV, and get matched jobs in one flow.
        </p>

        <div className="stepper">
          <span className={`step-pill ${stepIndex >= 0 ? 'active' : ''}`}>1. Profile</span>
          <span className={`step-pill ${stepIndex >= 1 ? 'active' : ''}`}>2. CV</span>
          <span className={`step-pill ${stepIndex >= 2 ? 'active' : ''}`}>3. Job Matches</span>
        </div>

        <p className="muted" style={{ margin: 0 }}>
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
        />
      ) : null}

      {step === 'results' ? (
        <div className="grid">
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
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
