'use client'

import {
  ErrorSummary,
  TextArea,
  TextField,
  useErrorTitle,
  useFormAction,
  type FormError,
} from '@cv/ui'
import type { ProfileFormState, ProfileValues } from './profileValues'
import styles from './ProfileForm.module.css'

type Props = {
  initialState: ProfileFormState
  action: (state: ProfileFormState, data: FormData) => Promise<ProfileFormState>
}

const ONE_A_LINE = 'One a line'

function summaryErrors({ errors, formError }: ProfileFormState): FormError[] {
  const fields = Object.entries(errors).map(([field, message]) => ({ href: `#${field}`, message }))
  return formError ? [{ message: formError }, ...fields] : fields
}

export function ProfileForm({ initialState, action }: Props) {
  const [state, formAction, pending] = useFormAction(action, initialState)
  const errorList = summaryErrors(state)

  useErrorTitle(errorList.length > 0)

  const { values, errors } = state
  const field = (name: keyof ProfileValues) => ({
    name,
    defaultValue: values[name],
    error: errors[name],
  })

  return (
    // The key resets the fields to what was saved, as the api may tidy what was sent.
    <form key={JSON.stringify(values)} action={formAction} noValidate className={styles.form}>
      <ErrorSummary errors={errorList} />
      {state.saved && (
        <p role="status" className={styles.saved}>
          Your profile has been saved. {/* The cv zone is another zone, so a plain link. */}
          <a href="/cv">Write your CV</a>
        </p>
      )}

      <section aria-labelledby="section-about" className={styles.section}>
        <h2 id="section-about">About you</h2>
        <TextField {...field('name')} label="Full name" autoComplete="name" />
        <TextField
          {...field('targetRole')}
          label="Role you want"
          hint="For example, Backend engineer"
          optional
        />
        <TextField
          {...field('yearsOfExperience')}
          label="Years of experience"
          width="short"
          optional
        />
        <TextField {...field('location')} label="Location" hint="For example, Dublin" optional />
        <TextField
          {...field('workAuthorization')}
          label="Right to work"
          hint="For example, EU citizen"
          optional
        />
      </section>

      <section aria-labelledby="section-skills" className={styles.section}>
        <h2 id="section-skills">Skills and experience</h2>
        <TextArea
          {...field('skills')}
          label="Skills"
          hint="Separate them with commas or put one a line. Jobs are matched to these."
        />
        <TextArea
          {...field('experience')}
          label="Experience"
          hint="The roles you have had and what you did in them"
        />
        <TextArea {...field('education')} label="Education" />
      </section>

      <section aria-labelledby="section-more" className={styles.section}>
        <h2 id="section-more">More about you</h2>
        <TextArea {...field('projects')} label="Projects" hint={ONE_A_LINE} optional />
        <TextArea {...field('certifications')} label="Certifications" hint={ONE_A_LINE} optional />
        <TextArea {...field('languages')} label="Languages" hint={ONE_A_LINE} optional />
      </section>

      <section aria-labelledby="section-contact" className={styles.section}>
        <h2 id="section-contact">Contact details</h2>
        <TextField
          {...field('email')}
          label="Email address"
          type="email"
          autoComplete="email"
          spellCheck={false}
          optional
        />
        <TextField
          {...field('phone')}
          label="Phone number"
          type="tel"
          autoComplete="tel"
          width="medium"
          optional
        />
        <TextField
          {...field('linkedin')}
          label="LinkedIn"
          hint="For example, https://linkedin.com/in/your-name"
          type="url"
          spellCheck={false}
          optional
        />
        <TextField
          {...field('portfolio')}
          label="Portfolio or website"
          type="url"
          spellCheck={false}
          optional
        />
      </section>

      <button type="submit" disabled={pending}>
        Save profile
      </button>
    </form>
  )
}
