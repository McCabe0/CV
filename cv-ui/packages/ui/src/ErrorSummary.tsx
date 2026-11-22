'use client'

import { useEffect, useId, useRef } from 'react'
import styles from './ErrorSummary.module.css'

export type FormError = {
  /** The field to go to, eg `#firstName`. Left out for an error that is not about one field. */
  href?: string
  message: string
}

/** The gov.uk error summary: at the top of the form, focused when shown, linked to each field. */
export function ErrorSummary({ errors }: { errors: FormError[] }) {
  const ref = useRef<HTMLElement>(null)
  const headingId = useId()

  useEffect(() => {
    ref.current?.focus()
  }, [errors])

  if (errors.length === 0) return null

  return (
    <section ref={ref} aria-labelledby={headingId} tabIndex={-1} className={styles.summary}>
      <h2 id={headingId} className={styles.heading}>
        There is a problem
      </h2>
      <ul className={styles.list}>
        {errors.map(({ href, message }) => (
          <li key={`${href}${message}`}>
            {href ? (
              <a
                href={href}
                // Not every browser moves focus on a same-page link, so move it explicitly.
                onClick={(event) => {
                  const field = document.getElementById(href.slice(1))
                  if (!field) return
                  event.preventDefault()
                  field.focus()
                }}
              >
                {message}
              </a>
            ) : (
              message
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
