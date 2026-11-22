import type { Metadata } from 'next'
import { startCv } from './actions'
import { StartCvForm } from './forms'

// The layout's title template only applies to nested pages, so the zone's root gives its full title.
export const metadata: Metadata = { title: 'Your CV — Skill2Career' }

export default function Page() {
  return (
    <>
      <h1>Your CV</h1>
      <p>
        Your CV is written only from your profile. First an interviewer may ask you a few questions
        about what your profile does not say. Then a writer drafts your CV, and a critic checks
        every claim against your profile until it is ready. It takes a minute or two.
      </p>
      <StartCvForm action={startCv} />
    </>
  )
}
