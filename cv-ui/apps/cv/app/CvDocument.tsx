import type { ReactNode } from 'react'
import type { Cv } from './cvRequest'
import styles from './CvDocument.module.css'

/** Who the CV is for, from their profile. The agents never see the contact details. */
export type Person = {
  name: string
  email?: string | null
  phone?: string | null
  location?: string | null
  linkedin?: string | null
  portfolio?: string | null
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  const id = `cv-${title.toLowerCase()}`
  return (
    <section aria-labelledby={id} className={styles.section}>
      <h3 id={id}>{title}</h3>
      {children}
    </section>
  )
}

function List({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null
  return (
    <Section title={title}>
      <ul className={styles.list}>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </Section>
  )
}

/** The finished CV, as the writer structured it. */
export function CvDocument({ cv, person }: { cv: Cv; person: Person }) {
  const where = [person.location, person.phone].filter(Boolean).join(' · ')
  const links = [person.linkedin, person.portfolio].filter((link): link is string => Boolean(link))

  return (
    <article className={styles.cv}>
      <header className={styles.header}>
        <h2>{person.name}</h2>
        {where && <p>{where}</p>}
        {(person.email || links.length > 0) && (
          <p className={styles.links}>
            {person.email && <a href={`mailto:${person.email}`}>{person.email}</a>}
            {links.map((link) => (
              <a key={link} href={link}>
                {link}
              </a>
            ))}
          </p>
        )}
      </header>

      <p className={styles.summary}>{cv.summary}</p>

      {cv.roles.length > 0 && (
        <Section title="Experience">
          {cv.roles.map((role) => (
            <div key={`${role.title}${role.company}`} className={styles.role}>
              <h4>
                {role.title}, {role.company}
              </h4>
              {role.dates && <p className={styles.dates}>{role.dates}</p>}
              <ul className={styles.list}>
                {role.bullets.map((bullet) => (
                  <li key={bullet}>{bullet}</li>
                ))}
              </ul>
            </div>
          ))}
        </Section>
      )}
      <List title="Skills" items={cv.skills} />
      <List title="Education" items={cv.education} />
      <List title="Projects" items={cv.projects} />
      <List title="Certifications" items={cv.certifications} />
      <List title="Languages" items={cv.languages} />
    </article>
  )
}
