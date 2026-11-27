import type { ReactNode } from 'react'
import { areasFor } from '@cv/auth'
import { getSession } from '@cv/auth/server'
import styles from './home.module.css'

// What each area is for, in a line, under its name, with its icon.
const ABOUT: Record<string, string> = {
  '/profile': 'Your experience, skills and what you are looking for.',
  '/cv': 'Answer a few questions and get a CV written and reviewed for you.',
  '/jobs': 'Roles that fit your profile, and why each one fits.',
  '/admin': 'The companies tracked, and how their crawls are going.',
}

// Simple line icons, drawn here so there is no icon library to load. Decorative only.
const icon = (path: ReactNode) => (
  <svg
    viewBox="0 0 24 24"
    width="22"
    height="22"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    {path}
  </svg>
)

const ICONS: Record<string, ReactNode> = {
  '/profile': icon(
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c0-4 3.6-7 8-7s8 3 8 7" />
    </>,
  ),
  '/cv': icon(
    <>
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
      <path d="M14 3v5h5M9 13h6M9 17h4" />
    </>,
  ),
  '/jobs': icon(
    <>
      <rect x="3" y="7" width="18" height="13" rx="2" />
      <path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2M3 13h18" />
    </>,
  ),
  '/admin': icon(
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1" />
    </>,
  ),
}

export default async function Home() {
  const user = (await getSession())?.user
  if (!user) return <Landing />

  const areas = areasFor(user)
  return (
    <>
      <div className={styles.welcome}>
        <p className="eyebrow">Your dashboard</p>
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
                <span className={styles.icon}>{ICONS[href]}</span>
                {/* Each area is a separate zone, so this must be a full page load, not a client side Link. */}
                <a href={href} className={styles.name}>
                  {name}
                </a>
                {ABOUT[href] && <p className={styles.about}>{ABOUT[href]}</p>}
                <span className={styles.open} aria-hidden="true">
                  Open →
                </span>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </>
  )
}

const STEPS = [
  {
    title: 'Tell us about you',
    text: 'Your experience, skills, education and the role you want next. It takes a few minutes, and you can come back to it.',
  },
  {
    title: 'Your CV team gets to work',
    text: 'The interviewer asks about what your profile leaves out. Then the writer drafts and the critic reviews, round by round, until it is good enough.',
  },
  {
    title: 'See the jobs that fit',
    text: "Live roles from companies' own job boards that ask for your skills, best first, and a CV aimed at any one of them.",
  },
]

const TEAM = [
  {
    name: 'The interviewer',
    initial: 'I',
    text: 'Finds the gaps a strong CV needs filled, like a bullet with no number or a skill with no evidence, and asks you up to five short questions.',
  },
  {
    name: 'The writer',
    initial: 'W',
    text: 'Drafts your CV from your profile and answers only. It never invents an employer, a date, a number or a skill.',
  },
  {
    name: 'The critic',
    initial: 'C',
    text: 'Scores each draft for facts, impact, keywords and a summary that is about you, and sends it back until it passes.',
  },
]

/** For someone signed out: what it does, how, and a way in. */
function Landing() {
  return (
    <div className={styles.landing}>
      <section className={styles.hero}>
        <div className={styles.heroText}>
          <p className="eyebrow">Your AI career team</p>
          <h1>A CV written for you, and the jobs it fits</h1>
          <p className={styles.lead}>
            Tell us what you have done. An interviewer, a writer and a critic turn it into a CV
            ready to send, then we find live jobs on companies&apos; own boards that ask for your
            skills.
          </p>
          <div className={styles.ctas}>
            {/* A plain link: sign in is a route handler that sends the user to keycloak. */}
            <a href="/auth/login" className="button-link">
              Sign in
            </a>
            <a href="#how-it-works" className="button-secondary">
              How it works
            </a>
          </div>
        </div>
        <Preview />
      </section>

      <section id="how-it-works" aria-labelledby="how-label" className={styles.section}>
        <p id="how-label" className="eyebrow">
          How it works
        </p>
        <h2>From your story to a CV, and the jobs it fits</h2>
        <ol className={styles.steps}>
          {STEPS.map((step, index) => (
            <li key={step.title} className={`card ${styles.step}`}>
              <span className={styles.number} aria-hidden="true">
                {index + 1}
              </span>
              <h3>{step.title}</h3>
              <p>{step.text}</p>
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="team-heading" className={styles.section}>
        <p className="eyebrow">The agents</p>
        <h2 id="team-heading">Meet your CV team</h2>
        <ul className={styles.team}>
          {TEAM.map((agent) => (
            <li key={agent.name} className={`card ${styles.agent}`}>
              <span className={styles.agentAvatar} aria-hidden="true">
                {agent.initial}
              </span>
              <h3>{agent.name}</h3>
              <p>{agent.text}</p>
            </li>
          ))}
        </ul>
        <p className={styles.note}>
          To write your CV, your profile is sent to Google&apos;s Gemini. Jobs come straight from
          companies&apos; own job boards, and you apply on their site.
        </p>
      </section>

      <section aria-labelledby="start-heading" className={styles.closing}>
        <h2 id="start-heading">Start with your profile</h2>
        <p>Your first CV is a few minutes away.</p>
        <a href="/auth/login" className={styles.closingCta}>
          Get started
        </a>
      </section>
    </div>
  )
}

/** A made up example of the team at work, beside the headline. Decorative, so hidden from screen readers. */
function Preview() {
  return (
    <div className={styles.preview} aria-hidden="true">
      <div className={styles.bubble}>
        <span className={styles.bubbleWho}>
          <span className={styles.miniAvatar}>I</span>The interviewer
        </span>
        You moved the orders service to Kafka. How much faster did orders go through afterwards?
      </div>
      <div className={`${styles.bubble} ${styles.reply}`}>About 40% faster, at twice the load.</div>
      <div className={styles.review}>
        <span className={styles.miniAvatar}>C</span>
        <span>
          Draft 2 scored <strong>92</strong>
        </span>
        <span className={styles.approved}>Approved</span>
      </div>
      <div className={styles.match}>
        <span className={styles.logo}>N</span>
        <span className={styles.matchText}>
          <strong>Backend Engineer</strong>
          <span>Northwind · Remote</span>
        </span>
        <span className="chip">Kotlin</span>
      </div>
      <p className={styles.example}>An example</p>
    </div>
  )
}
