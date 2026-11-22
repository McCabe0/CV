import type { Metadata } from 'next'
import type { ReactNode } from 'react'
import { requireSession } from '@cv/auth/server'
import { AppLayout } from '@cv/ui'
import '@cv/ui/styles.css'
import { BASE_PATH } from './basePath'

export const metadata: Metadata = {
  title: { template: '%s — Skill2Career', default: 'Skill2Career' },
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  // The proxy has already required sign in and the candidate role.
  const { user } = await requireSession(BASE_PATH)

  return (
    <html lang="en-GB">
      <body>
        <AppLayout area="Profile" user={user}>
          {children}
        </AppLayout>
      </body>
    </html>
  )
}
