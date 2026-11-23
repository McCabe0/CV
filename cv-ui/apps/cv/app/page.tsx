import type { Metadata } from 'next'
import { startCv } from './actions'
import { StartCvForm } from './forms'

// The layout's title template only applies to nested pages, so the zone's root gives its full title.
export const metadata: Metadata = { title: 'Your CV — Skill2Career' }

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> }

/** The job chosen in the jobs area, which links here with ?job=42; none for a general CV. */
function jobIdFrom(job: string | string[] | undefined): number | undefined {
  const jobId = Number(Array.isArray(job) ? job[0] : job)
  return Number.isInteger(jobId) && jobId > 0 ? jobId : undefined
}

export default async function Page({ searchParams }: Props) {
  const jobId = jobIdFrom((await searchParams).job)

  return (
    <>
      <h1>{jobId ? 'A CV for one job' : 'Your CV'}</h1>
      <p>
        Your CV is written only from your profile
        {jobId ? ', aimed at the job you chose: its summary, skills and keywords' : ''}. First an
        interviewer may ask you a few questions about what your profile does not say. Then a writer
        drafts your CV, and a critic checks every claim against your profile until it is ready. It
        takes a minute or two.
      </p>
      <StartCvForm action={startCv} jobId={jobId} />
    </>
  )
}
