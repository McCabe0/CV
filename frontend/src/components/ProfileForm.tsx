import React, { FormEvent, useMemo, useState } from 'react'
import type { Profile, WorkExperience } from '../api'

type ProfileFormProps = {
  initialValue?: Profile
  loading?: boolean
  error?: string | null
  onSubmit: (profile: Profile) => void
}

const SAMPLE_PROFILE: Profile = {
  name: 'Jordan Lee',
  skills: ['Python', 'SQL', 'Tableau', 'Airflow', 'AWS', 'Machine Learning', 'A/B Testing', 'Data Modeling'],
  experience:
    'Data analyst with 5+ years building dashboards, ETL workflows, and predictive models for e-commerce and fintech teams. Led weekly analytics reviews with product and engineering stakeholders.',
  education: 'B.S. in Computer Science, University of Washington (2019)',
  targetRole: 'Senior Data Analyst',
  yearsOfExperience: '5',
  location: 'Seattle, WA',
  workAuthorization: 'US Citizen',
  projects: ['Customer churn prediction model', 'Real-time KPI dashboard migration', 'Experimentation analytics framework'],
  certifications: ['AWS Certified Cloud Practitioner', 'Google Data Analytics Professional Certificate'],
  languages: ['English', 'Spanish'],
  email: 'jordan.lee@example.com',
  phone: '+1 (206) 555-0148',
  linkedin: 'linkedin.com/in/jordanlee',
  portfolio: 'github.com/jordanlee',
  workHistory: [
    {
      company: 'Northwind Commerce',
      title: 'Data Analyst',
      startDate: 'Jun 2021',
      endDate: 'Present',
      bullets: [
        'Built ETL pipelines in Airflow feeding a Snowflake warehouse used by 40+ analysts.',
        'Shipped a churn prediction model that cut monthly churn by 12%.',
      ],
    },
    {
      company: 'BrightPay Fintech',
      title: 'Junior Data Analyst',
      startDate: 'Jul 2019',
      endDate: 'May 2021',
      bullets: ['Automated weekly KPI dashboards in Tableau, saving ~6 analyst-hours per week.'],
    },
  ],
}

function emptyRole(): WorkExperience {
  return { company: '', title: '', startDate: '', endDate: '', bullets: [] }
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
  const [email, setEmail] = useState(initialValue?.email ?? '')
  const [phone, setPhone] = useState(initialValue?.phone ?? '')
  const [linkedin, setLinkedin] = useState(initialValue?.linkedin ?? '')
  const [portfolio, setPortfolio] = useState(initialValue?.portfolio ?? '')
  const [workHistory, setWorkHistory] = useState<WorkExperience[]>(initialValue?.workHistory ?? [])

  const updateRole = (index: number, patch: Partial<WorkExperience>) => {
    setWorkHistory((roles) => roles.map((role, i) => (i === index ? { ...role, ...patch } : role)))
  }
  const addRole = () => setWorkHistory((roles) => [...roles, emptyRole()])
  const removeRole = (index: number) => setWorkHistory((roles) => roles.filter((_, i) => i !== index))

  const isValid = useMemo(() => {
    return Boolean(name.trim() && splitList(skills).length > 0 && experience.trim() && education.trim())
  }, [education, experience, name, skills])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const cleanedWorkHistory = workHistory
      .map((role) => ({
        company: role.company.trim(),
        title: role.title.trim(),
        startDate: role.startDate?.trim() || undefined,
        endDate: role.endDate?.trim() || undefined,
        bullets: role.bullets.map((b) => b.trim()).filter(Boolean),
      }))
      .filter((role) => role.company || role.title || role.bullets.length > 0)

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
      email: email.trim() || undefined,
      phone: phone.trim() || undefined,
      linkedin: linkedin.trim() || undefined,
      portfolio: portfolio.trim() || undefined,
      workHistory: cleanedWorkHistory,
    })
  }

  const applySample = () => {
    setName(SAMPLE_PROFILE.name)
    setSkills(joinList(SAMPLE_PROFILE.skills))
    setExperience(SAMPLE_PROFILE.experience)
    setEducation(SAMPLE_PROFILE.education)
    setTargetRole(SAMPLE_PROFILE.targetRole ?? '')
    setYearsOfExperience(SAMPLE_PROFILE.yearsOfExperience ?? '')
    setLocation(SAMPLE_PROFILE.location ?? '')
    setWorkAuthorization(SAMPLE_PROFILE.workAuthorization ?? '')
    setProjects(joinList(SAMPLE_PROFILE.projects))
    setCertifications(joinList(SAMPLE_PROFILE.certifications))
    setLanguages(joinList(SAMPLE_PROFILE.languages))
    setEmail(SAMPLE_PROFILE.email ?? '')
    setPhone(SAMPLE_PROFILE.phone ?? '')
    setLinkedin(SAMPLE_PROFILE.linkedin ?? '')
    setPortfolio(SAMPLE_PROFILE.portfolio ?? '')
    setWorkHistory(SAMPLE_PROFILE.workHistory ?? [])
  }

  return (
    <form onSubmit={handleSubmit} className="card grid">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
        <h2 style={{ margin: 0 }}>Step 1: Build your profile</h2>
        <button type="button" onClick={applySample}>
          Use dummy data
        </button>
      </div>
      <p className="muted" style={{ margin: 0 }}>Start with the basics and we’ll generate a CV draft for you.</p>

      <div className="grid-2">
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Full name" required />
        <input value={targetRole} onChange={(e) => setTargetRole(e.target.value)} placeholder="Target role (e.g. Data Analyst)" />
      </div>

      <input
        value={skills}
        onChange={(e) => setSkills(e.target.value)}
        placeholder="Skills (comma-separated)"
        required
      />

      <div className="grid-2">
        <input value={yearsOfExperience} onChange={(e) => setYearsOfExperience(e.target.value)} placeholder="Years of experience" />
        <input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Location" />
      </div>

      <textarea value={experience} onChange={(e) => setExperience(e.target.value)} placeholder="Experience" required rows={4} />
      <textarea value={education} onChange={(e) => setEducation(e.target.value)} placeholder="Education" required rows={2} />

      <div className="grid-2">
        <input value={workAuthorization} onChange={(e) => setWorkAuthorization(e.target.value)} placeholder="Work authorization" />
        <input value={languages} onChange={(e) => setLanguages(e.target.value)} placeholder="Languages (comma-separated)" />
      </div>

      <div className="grid-2">
        <input value={projects} onChange={(e) => setProjects(e.target.value)} placeholder="Projects (comma-separated)" />
        <input value={certifications} onChange={(e) => setCertifications(e.target.value)} placeholder="Certifications (comma-separated)" />
      </div>

      <h3 style={{ margin: '4px 0 0' }}>Contact details</h3>
      <div className="grid-2">
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" />
        <input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="Phone" />
      </div>
      <div className="grid-2">
        <input value={linkedin} onChange={(e) => setLinkedin(e.target.value)} placeholder="LinkedIn URL" />
        <input value={portfolio} onChange={(e) => setPortfolio(e.target.value)} placeholder="Portfolio / GitHub URL" />
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
        <h3 style={{ margin: 0 }}>Work history</h3>
        <button type="button" onClick={addRole}>
          Add role
        </button>
      </div>
      {workHistory.length === 0 ? (
        <p className="muted" style={{ margin: 0 }}>
          Optional, but adding roles produces a far more accurate CV. Click “Add role” to start.
        </p>
      ) : null}
      {workHistory.map((role, index) => (
        <div key={index} className="card grid" style={{ background: '#f8fafc' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
            <strong>Role {index + 1}</strong>
            <button type="button" onClick={() => removeRole(index)}>
              Remove
            </button>
          </div>
          <div className="grid-2">
            <input value={role.title} onChange={(e) => updateRole(index, { title: e.target.value })} placeholder="Job title" />
            <input value={role.company} onChange={(e) => updateRole(index, { company: e.target.value })} placeholder="Company" />
          </div>
          <div className="grid-2">
            <input
              value={role.startDate ?? ''}
              onChange={(e) => updateRole(index, { startDate: e.target.value })}
              placeholder="Start date (e.g. Jun 2021)"
            />
            <input
              value={role.endDate ?? ''}
              onChange={(e) => updateRole(index, { endDate: e.target.value })}
              placeholder="End date (e.g. Present)"
            />
          </div>
          <textarea
            value={role.bullets.join('\n')}
            onChange={(e) => updateRole(index, { bullets: e.target.value.split('\n') })}
            placeholder="Responsibilities & achievements (one per line)"
            rows={3}
          />
        </div>
      ))}

      {error ? <p className="error">{error}</p> : null}

      <button type="submit" disabled={!isValid || loading}>
        {loading ? 'Generating CV...' : 'Generate CV'}
      </button>
    </form>
  )
}
