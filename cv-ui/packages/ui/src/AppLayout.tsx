import type { ReactNode } from 'react'
import { SkipLink } from './SkipLink'
import styles from './AppLayout.module.css'

const MAIN_CONTENT_ID = 'main-content'

type Props = {
  area: string
  /** The signed in user, if any. */
  user?: { name: string }
  children: ReactNode
}

export function AppLayout({ area, user, children }: Props) {
  return (
    <>
      <SkipLink targetId={MAIN_CONTENT_ID} />
      <header className={styles.header}>
        <div className={`${styles.container} ${styles.banner}`}>
          <p className={styles.service}>Skill2Career · {area}</p>
          {user && (
            <div className={styles.account}>
              <p className={styles.user}>Signed in as {user.name}</p>
              {/* Sign out lives in the shell. A post, so following a link can never sign someone out. */}
              <form action="/auth/logout" method="post">
                <button type="submit">Sign out</button>
              </form>
            </div>
          )}
        </div>
      </header>
      <main id={MAIN_CONTENT_ID} tabIndex={-1} className={`${styles.container} ${styles.main}`}>
        {children}
      </main>
    </>
  )
}
