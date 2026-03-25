import React, { FormEvent, useMemo, useState } from 'react'
import type { Profile } from '../api'

type ProfileFormProps = {
  initialValue?: Profile
  loading?: boolean
  error?: string | null
  onSubmit: (profile: Profile) => void
}

function splitList(value: string): string[] {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function joinList(value: string[] | undefined): string {
  return value?.join(', ') ?? ''
}

export default function ProfileForm({ initialValue, loading, error, onSubmit }: ProfileFormProps) {
  const [name, setName] = useState(initialValue?.name ?? '')
  const [skills, setSkills] = useState(joinList(initialValue?.skills))
  const [experience, setExperience] = useState(initialValue?.experience ?? '')
  const [education, setEducation] = useState(initialValue?.education ?? '')
  const [targetRole, setTargetRole] = useState(initialValue?.targetRole ?? '')
  const [yearsOfExperience, setYearsOfExperience] = useState(initialValue?.yearsOfExperience ?? '')
  const [location, setLocation] = useState(initialValue?.location ?? '')
  const [workAuthorization, setWorkAuthorization] = useState(initialValue?.workAuthorization ?? '')
  const [projects, setProjects] = useState(joinList(initialValue?.projects))
  const [certifications, setCertifications] = useState(joinList(initialValue?.certifications))
  const [languages, setLanguages] = useState(joinList(initialValue?.languages))

  const isValid = useMemo(() => {
    return name.trim() && splitList(skills).length > 0 && experience.trim() && education.trim()
  }, [education, experience, name, skills])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit({
      name: name.trim(),
      skills: splitList(skills),
      experience: experience.trim(),
      education: education.trim(),
      targetRole: targetRole.trim() || undefined,
      yearsOfExperience: yearsOfExperience.trim() || undefined,
      location: location.trim() || undefined,
      workAuthorization: workAuthorization.trim() || undefined,
      projects: splitList(projects),
      certifications: splitList(certifications),
      languages: splitList(languages),
    })
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'grid', gap: 12, maxWidth: 900 }}>
      <h2>Step 1: Profile form</h2>
      <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Full name" required />
      <input
        value={skills}
        onChange={(e) => setSkills(e.target.value)}
        placeholder="Skills (comma-separated)"
        required
      />
      <textarea
        value={experience}
        onChange={(e) => setExperience(e.target.value)}
        placeholder="Experience"
        required
        rows={4}
      />
      <textarea
        value={education}
        onChange={(e) => setEducation(e.target.value)}
        placeholder="Education"
        required
        rows={2}
      />
      <input value={targetRole} onChange={(e) => setTargetRole(e.target.value)} placeholder="Target role" />
      <input
        value={yearsOfExperience}
        onChange={(e) => setYearsOfExperience(e.target.value)}
        placeholder="Years of experience"
      />
      <input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Location" />
      <input
        value={workAuthorization}
        onChange={(e) => setWorkAuthorization(e.target.value)}
        placeholder="Work authorization"
      />
      <input value={projects} onChange={(e) => setProjects(e.target.value)} placeholder="Projects (comma-separated)" />
      <input
        value={certifications}
        onChange={(e) => setCertifications(e.target.value)}
        placeholder="Certifications (comma-separated)"
      />
      <input value={languages} onChange={(e) => setLanguages(e.target.value)} placeholder="Languages (comma-separated)" />

      {error ? <p style={{ color: 'crimson' }}>{error}</p> : null}

      <button type="submit" disabled={!isValid || loading}>
        {loading ? 'Generating CV...' : 'Generate CV'}
      </button>
    </form>
  )
}
