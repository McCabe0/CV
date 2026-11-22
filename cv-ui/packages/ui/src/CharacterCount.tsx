'use client'

import { useState } from 'react'
import { controlProps, Field, type Common } from './fields'
import styles from './fields.module.css'

type Props = Common & {
  maxLength: number
  defaultValue?: string
}

const characters = (n: number) =>
  `${n.toLocaleString('en-GB')} ${n === 1 ? 'character' : 'characters'}`

/**
 * A text box that shows how many characters are left, as gov.uk's character count does. It never stops
 * the typing, so a long paste can be trimmed; the form refuses what is too long when it is sent.
 */
export function CharacterCount({ maxLength, defaultValue = '', ...common }: Props) {
  const [length, setLength] = useState(defaultValue.length)
  const countId = `${common.name}-count`
  const control = controlProps(common)
  const over = length - maxLength

  return (
    <Field {...common}>
      <textarea
        {...control}
        aria-describedby={[control['aria-describedby'], countId].filter(Boolean).join(' ')}
        defaultValue={defaultValue}
        onChange={(event) => setLength(event.target.value.length)}
        rows={8}
        className={styles.input}
      />
      <p
        id={countId}
        aria-live="polite"
        className={over > 0 ? `${styles.count} ${styles.countOver}` : styles.count}
      >
        {over > 0
          ? `You have ${characters(over)} too many`
          : `You have ${characters(maxLength - length)} remaining`}
      </p>
    </Field>
  )
}
