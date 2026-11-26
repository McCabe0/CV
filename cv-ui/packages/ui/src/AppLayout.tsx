import type { ReactNode } from 'react'
import { SkipLink } from './SkipLink'
import styles from './AppLayout.module.css'

const MAIN_CONTENT_ID = 'main-content'
// The shell's home. A zone of its own, so it is reached by a plain link, like every area.
const HOME = { name: 'Home', href: '/' }

export type NavItem = { name: string; href: string }

type Props = {
  /** The href of the area this page belongs to, marked as current in the navigation. */
  current?: string
  /** The areas the user may use, shown as the main navigation. */
  areas?: NavItem[]
  /** The signed in user, if any. */
  user?: { name: string }
  children: ReactNode
}

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('')

export function AppLayout({ current, areas = [], user, children }: Props) {
  return (
    <>
      <SkipLink targetId={MAIN_CONTENT_ID} />
      <header className={styles.header}>
        <div className={`${styles.container} ${styles.banner}`}>
          {/* Each area is a separate zone, so every link here is a plain one, for a full page load. */}
          <a href={HOME.href} className={styles.brand}>
            <span className={styles.mark} aria-hidden="true">
              S2C
            </span>
            Skill2Career
          </a>
          {user && (
            <div className={styles.account}>
              <span className={styles.avatar} aria-hidden="true">
                {initials(user.name)}
              </span>
              <p className={styles.user}>
                <span className="visually-hidden">Signed in as </span>
                {user.name}
              </p>
              {/* Sign out lives in the shell. A post, so following a link can never sign someone out. */}
              <form action="/auth/logout" method="post">
                <button type="submit" className={styles.signOut}>
                  Sign out
                </button>
              </form>
            </div>
          )}
        </div>
        {/* Home leads the areas, so a user with any of them can always get back. */}
        {areas.length > 0 && (
          <nav aria-label="Main" className={styles.nav}>
            <ul className={`${styles.container} ${styles.navList}`}>
              {[HOME, ...areas].map(({ name, href }) => (
                <li key={href}>
                  <a
                    href={href}
                    className={styles.navLink}
                    aria-current={href === current ? 'true' : undefined}
                  >
                    {name}
                  </a>
                </li>
              ))}
            </ul>
          </nav>
        )}
      </header>
      <main id={MAIN_CONTENT_ID} tabIndex={-1} className={`${styles.container} ${styles.main}`}>
        {children}
      </main>
    </>
  )
}
