'use client'

import styles from './SkipLink.module.css'

export function SkipLink({ targetId }: { targetId: string }) {
  return (
    <a
      href={`#${targetId}`}
      className={styles.skipLink}
      // Not every browser moves focus on a same-page link, so move it explicitly.
      onClick={() => document.getElementById(targetId)?.focus()}
    >
      Skip to main content
    </a>
  )
}
