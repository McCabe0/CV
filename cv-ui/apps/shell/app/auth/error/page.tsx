import type { Metadata } from 'next'

export const metadata: Metadata = { title: 'Sign in failed' }

export default function SignInError() {
  return (
    <>
      <h1>Sign in failed</h1>
      <p>We could not sign you in. This can happen if signing in took too long.</p>
      <p>
        <a href="/auth/login">Try signing in again</a>
      </p>
    </>
  )
}
