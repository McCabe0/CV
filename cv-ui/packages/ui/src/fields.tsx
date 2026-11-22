import type { InputHTMLAttributes, ReactNode } from 'react'
import styles from './fields.module.css'

export type Common = {
  /** The form field's name, and its id, so the error summary can link to it. */
  name: string
  label: string
  hint?: string
  error?: string
  optional?: boolean
}

const hintId = (name: string) => `${name}-hint`
const errorId = (name: string) => `${name}-error`

/** Ids of the hint and error, for aria-describedby. */
function describedBy({ name, hint, error }: Pick<Common, 'name' | 'hint' | 'error'>) {
  const ids = [hint && hintId(name), error && errorId(name)].filter(Boolean)
  return ids.length > 0 ? ids.join(' ') : undefined
}

function HintAndError({ name, hint, error }: Pick<Common, 'name' | 'hint' | 'error'>) {
  return (
    <>
      {hint && (
        <p id={hintId(name)} className={styles.hint}>
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId(name)} className={styles.error}>
          <span className="visually-hidden">Error:</span> {error}
        </p>
      )}
    </>
  )
}

export function Field({
  name,
  label,
  hint,
  error,
  optional,
  children,
}: Common & { children: ReactNode }) {
  return (
    <div className={error ? `${styles.field} ${styles.invalid}` : styles.field}>
      <label htmlFor={name} className={styles.label}>
        {label}
        {optional && ' (optional)'}
      </label>
      <HintAndError name={name} hint={hint} error={error} />
      {children}
    </div>
  )
}

export const controlProps = ({ name, hint, error }: Common) => ({
  id: name,
  name,
  'aria-describedby': describedBy({ name, hint, error }),
  'aria-invalid': error ? true : undefined,
})

type TextFieldProps = Common &
  Pick<
    InputHTMLAttributes<HTMLInputElement>,
    'type' | 'autoComplete' | 'inputMode' | 'spellCheck' | 'defaultValue'
  > & { width?: 'short' | 'medium' }

export function TextField({
  type = 'text',
  autoComplete,
  inputMode,
  spellCheck,
  defaultValue,
  width,
  ...common
}: TextFieldProps) {
  return (
    <Field {...common}>
      <input
        {...controlProps(common)}
        type={type}
        autoComplete={autoComplete}
        inputMode={inputMode}
        spellCheck={spellCheck}
        defaultValue={defaultValue}
        className={`${styles.input} ${width ? styles[width] : ''}`}
      />
    </Field>
  )
}

export function TextArea({ defaultValue, ...common }: Common & { defaultValue?: string }) {
  return (
    <Field {...common}>
      <textarea
        {...controlProps(common)}
        defaultValue={defaultValue}
        rows={5}
        className={styles.input}
      />
    </Field>
  )
}

type Option = {
  value: string
  label: string
  /** Said after the label, for radios. */ hint?: string
}

export function SelectField({
  options,
  defaultValue = '',
  ...common
}: Common & { options: Option[]; defaultValue?: string }) {
  return (
    <Field {...common}>
      <select {...controlProps(common)} defaultValue={defaultValue} className={styles.select}>
        <option value="">Select an option</option>
        {options.map(({ value, label }) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
    </Field>
  )
}

export type DateValue = { day: string; month: string; year: string }

type DateFieldProps = Omit<Common, 'label' | 'optional'> & {
  legend: string
  defaultValue?: DateValue
  /** `bday` for a date of birth. */
  autoComplete?: 'bday'
}

const dateParts = [
  { part: 'day', label: 'Day', width: styles.day },
  { part: 'month', label: 'Month', width: styles.day },
  { part: 'year', label: 'Year', width: styles.year },
] as const

/** The gov.uk date input: separate day, month and year, which is easier to get right than a date picker. */
export function DateField({
  name,
  legend,
  hint,
  error,
  defaultValue,
  autoComplete,
}: DateFieldProps) {
  return (
    <fieldset
      className={
        error
          ? `${styles.field} ${styles.invalid} ${styles.fieldset}`
          : `${styles.field} ${styles.fieldset}`
      }
      aria-describedby={describedBy({ name, hint, error })}
    >
      <legend className={styles.label}>{legend}</legend>
      <HintAndError name={name} hint={hint} error={error} />
      <div className={styles.date}>
        {dateParts.map(({ part, label, width }) => (
          <div key={part}>
            <label htmlFor={`${name}-${part}`} className={styles.dateLabel}>
              {label}
            </label>
            <input
              id={`${name}-${part}`}
              name={`${name}-${part}`}
              type="text"
              inputMode="numeric"
              autoComplete={autoComplete && `${autoComplete}-${part}`}
              defaultValue={defaultValue?.[part]}
              aria-invalid={error ? true : undefined}
              className={`${styles.input} ${width}`}
            />
          </div>
        ))}
      </div>
    </fieldset>
  )
}

type RadiosProps = Omit<Common, 'label' | 'optional'> & {
  legend: string
  options: Option[]
  defaultValue?: string
}

/** A choice of one, as a group of radios. The first radio has the name as its id, so an error can link to it. */
export function Radios({ name, legend, hint, error, options, defaultValue }: RadiosProps) {
  return (
    <fieldset
      className={
        error
          ? `${styles.field} ${styles.invalid} ${styles.fieldset}`
          : `${styles.field} ${styles.fieldset}`
      }
      aria-describedby={describedBy({ name, hint, error })}
    >
      <legend className={styles.label}>{legend}</legend>
      <HintAndError name={name} hint={hint} error={error} />
      {options.map(({ value, label, hint: itemHint }, index) => {
        const id = index === 0 ? name : `${name}-${value}`
        return (
          <div key={value} className={styles.radio}>
            <input
              id={id}
              type="radio"
              name={name}
              value={value}
              defaultChecked={value === defaultValue}
              aria-describedby={itemHint ? `${id}-item-hint` : undefined}
            />
            <div>
              <label htmlFor={id}>{label}</label>
              {itemHint && (
                <p id={`${id}-item-hint`} className={styles.hint}>
                  {itemHint}
                </p>
              )}
            </div>
          </div>
        )
      })}
    </fieldset>
  )
}
