import type { Metadata } from 'next'
import type { ReactNode } from 'react'
import { getSession } from '@cv/auth/server'
import { AppLayout } from '@cv/ui'
import '@cv/ui/styles.css'

export const metadata: Metadata = {
  title: { template: '%s — Skill2Career', default: 'Skill2Career' },
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const user = (await getSession())?.user

  return (
    <html lang="en-GB">
      <body>
        <AppLayout area="Home" user={user}>
          {children}
        </AppLayout>
      </body>
    </html>
  )
}
