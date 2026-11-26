import { areasFor } from '@cv/auth'
import { getSession } from '@cv/auth/server'
import styles from './home.module.css'

// What each area is for, in a line, under its name.
const ABOUT: Record<string, string> = {
  '/profile': 'Your experience, skills and what you are looking for.',
  '/cv': 'Answer a few questions and get a CV written and reviewed for you.',
  '/jobs': 'Roles that fit your profile, and why each one fits.',
  '/admin': 'The companies tracked, and how their crawls are going.',
}

export default async function Home() {
  const user = (await getSession())?.user
  if (!user)
    return (
      <div className={styles.intro}>
        <h1>Skill2Career</h1>
        <p className={styles.lead}>A CV written for you, and the jobs it fits.</p>
        {/* A plain link: sign in is a route handler that sends the user to keycloak. */}
        <a href="/auth/login" className={styles.cta}>
          Sign in
        </a>
      </div>
    )

  const areas = areasFor(user)
  return (
    <>
      <div className={styles.intro}>
        <h1>Welcome, {user.name}</h1>
        {areas.length > 0 && <p className={styles.lead}>Where would you like to start?</p>}
      </div>
      {areas.length === 0 ? (
        <p>Your account does not have access to anything yet.</p>
      ) : (
        <nav aria-label="Your areas">
          <ul className={styles.areas}>
            {areas.map(({ name, href }) => (
              <li key={href} className={styles.area}>
                {/* Each area is a separate zone, so this must be a full page load, not a client side Link. */}
                <a href={href} className={styles.name}>
                  {name}
                </a>
                {ABOUT[href] && <p className={styles.about}>{ABOUT[href]}</p>}
              </li>
            ))}
          </ul>
        </nav>
      )}
    </>
  )
}
