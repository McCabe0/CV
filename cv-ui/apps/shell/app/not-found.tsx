import type { Metadata } from 'next'
import { NotFound } from '@cv/ui'

export const metadata: Metadata = { title: 'Page not found' }

/** In place of next's own, which is not in the service's layout and uses inline styles the content security policy blocks. */
export default function NotFoundPage() {
  return <NotFound />
}
