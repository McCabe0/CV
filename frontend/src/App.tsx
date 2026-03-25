import React, { useState } from 'react'
import {
  generateCv,
  getRecommendations,
  matchJobs,
  searchJobs,
  type CvResponse,
  type JobMatchResult,
  type Profile,
} from './api'
import CvPreview from './components/CvPreview'
import JobMatchList from './components/JobMatchList'
import ProfileForm from './components/ProfileForm'

type Step = 'profile' | 'cv' | 'results'

export default function App() {
  const [step, setStep] = useState<Step>('profile')
  const [profile, setProfile] = useState<Profile | null>(null)
  const [profileId, setProfileId] = useState<number | null>(null)
  const [cvId, setCvId] = useState<number | null>(null)
  const [cv, setCv] = useState<CvResponse | null>(null)
  const [matches, setMatches] = useState<JobMatchResult[]>([])

  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [cvLoading] = useState(false)
  const [cvError, setCvError] = useState<string | null>(null)
  const [resultsLoading, setResultsLoading] = useState(false)
  const [resultsError, setResultsError] = useState<string | null>(null)

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

  const handleFindMatches = async (criteria: { roleKeywords: string[]; location?: string }) => {
    if (!cv || !profile) {
      setCvError('CV and profile data are required before matching jobs.')
      return
    }

    setResultsLoading(true)
    setResultsError(null)
    setMatches([])

    try {
      const searchResponse = await searchJobs({
        skills: cv.keySkills.length > 0 ? cv.keySkills : profile.skills,
        location: criteria.location,
        roleKeywords: criteria.roleKeywords,
      })

      if (searchResponse.jobs.length === 0) {
        setStep('results')
        return
      }

      const cvText = [cv.headline, cv.summary, ...cv.experienceBullets, cv.educationSection].join('\n')
      const matchResponse = await matchJobs({
        profileId: profileId ?? undefined,
        cvId: cvId ?? undefined,
        generatedCvOrProfile: cvText,
        profileSkills: cv.keySkills,
        jobs: searchResponse.jobs,
      })

      setMatches(matchResponse.matches)
      setStep('results')
    } catch (error) {
      setResultsError(error instanceof Error ? error.message : 'Failed to find job matches')
      setStep('results')
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
    <div style={{ padding: 20, fontFamily: 'sans-serif', display: 'grid', gap: 16 }}>
      <h1>Skill2Career</h1>
      <p>
        Profile ID: <strong>{profileId ?? 'N/A'}</strong> | CV ID: <strong>{cvId ?? 'N/A'}</strong>
      </p>

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
        <>
          <button type="button" onClick={handleRefreshRecommendations} disabled={resultsLoading || !profileId} style={{ width: 'fit-content' }}>
            {resultsLoading ? 'Refreshing...' : 'Refresh recommendations'}
          </button>
          <JobMatchList
            matches={matches}
            loading={resultsLoading}
            error={resultsError}
            onBackToCv={() => setStep('cv')}
          />
        </>
      ) : null}
    </div>
  )
}
