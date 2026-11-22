import type { Page } from '@playwright/test'
import { expect, test } from './test'
import { expectAccessible, expectNoHorizontalScroll, signInOnKeycloak } from './support'

const field = (page: Page, name: string) => page.getByRole('textbox', { name, exact: true })

/** Signs in to the profile page, then waits for react to take over the form, which would otherwise reset what is typed first. */
async function openProfile(page: Page, username: string) {
  await page.goto('/profile')
  await signInOnKeycloak(page, username)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Your profile')
  await page.waitForLoadState('networkidle')
}

test('a candidate saves their profile, and it is there when they come back', async ({ page }) => {
  // Unique to this run, as the profile is kept from earlier runs.
  const role = `Backend engineer ${Date.now()}`
  await openProfile(page, 'candidate')

  await field(page, 'Full name').fill('Casey Candidate')
  await field(page, 'Role you want (optional)').fill(role)
  await field(page, 'Skills').fill('Kotlin, SQL, kotlin')
  await field(page, 'Experience').fill('Five years building APIs.')
  await field(page, 'Education').fill('BSc Computer Science')
  await field(page, 'Languages (optional)').fill('English\nIrish')
  await field(page, 'Email address (optional)').fill('casey@example.com')
  await page.getByRole('button', { name: 'Save profile' }).click()

  await expect(page.getByRole('status')).toHaveText('Your profile has been saved.')
  // The api keeps a skill given twice once, and the form shows what was kept.
  await expect(field(page, 'Skills')).toHaveValue('Kotlin, SQL')
  await expectAccessible(page)
  await expectNoHorizontalScroll(page)

  await page.reload()

  await expect(field(page, 'Role you want (optional)')).toHaveValue(role)
  await expect(field(page, 'Skills')).toHaveValue('Kotlin, SQL')
  await expect(field(page, 'Languages (optional)')).toHaveValue('English\nIrish')
  await expect(field(page, 'Email address (optional)')).toHaveValue('casey@example.com')
})

test('a candidate is told what is wrong, and taken to it', async ({ page }) => {
  await openProfile(page, 'candidate')

  await field(page, 'Skills').fill('')
  await field(page, 'Email address (optional)').fill('not an email')
  await page.getByRole('button', { name: 'Save profile' }).click()

  await expect(page).toHaveTitle('Error: Your profile — Skill2Career')
  const summary = page.getByRole('region', { name: 'There is a problem' })
  await expect(summary).toBeFocused()
  await expect(summary.getByRole('link')).toHaveText([
    'Enter at least one skill',
    'Enter an email address like name@example.com',
  ])
  await expectAccessible(page)

  await summary.getByRole('link', { name: 'Enter at least one skill' }).click()

  await expect(field(page, 'Skills')).toBeFocused()
})

test("one candidate never sees another's profile", async ({ page }) => {
  await openProfile(page, 'candidate-other')

  await expect(page.getByRole('banner')).toContainText('Signed in as Olly Other')
  await expect(field(page, 'Email address (optional)')).not.toHaveValue('casey@example.com')
  await expect(field(page, 'Full name')).not.toHaveValue('Casey Candidate')
})
