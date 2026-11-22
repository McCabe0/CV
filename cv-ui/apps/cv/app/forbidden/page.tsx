import type { Metadata } from 'next'
import { AccessDenied } from '@cv/ui'

export const metadata: Metadata = { title: 'You cannot access this' }

/** The proxy shows this, with a 403, to an account that is not a candidate. */
export default function Forbidden() {
  return <AccessDenied workspace="candidate CVs" />
}
