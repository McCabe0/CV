import AxeBuilder from '@axe-core/playwright'
import { expect, type Page } from '@playwright/test'

/** Every page we own must pass axe for WCAG 2.2 AA. */
export async function expectAccessible(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze()
  expect(results.violations).toEqual([])
}

/** WCAG reflow: nothing scrolls sideways, checked at 320px by the reflow project. */
export async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
  expect(overflow).toBeLessThanOrEqual(0)
}

/** Signs in on keycloak's own login page as one of the test realm's users. */
export async function signInOnKeycloak(page: Page, username: string) {
  await expect(page).toHaveURL(/^http:\/\/localhost:8180\/realms\/skill2career\//)
  await page.getByLabel('Username or email').fill(username)
  await page.getByLabel('Password', { exact: true }).fill('test')
  await page.getByRole('button', { name: 'Sign In' }).click()
}

/**
 * Signs in as the candidate, and saves a profile, so there is something to write a CV from and to match
 * jobs against. Its skills, Kotlin and PostgreSQL, are what the played job boards' jobs ask for.
 */
export async function withProfile(page: Page) {
  await page.goto('/profile')
  await signInOnKeycloak(page, 'candidate')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Your profile')
  await page.waitForLoadState('networkidle')
  await page.getByRole('textbox', { name: 'Full name', exact: true }).fill('Casey Candidate')
  await page.getByRole('textbox', { name: 'Skills', exact: true }).fill('Kotlin, PostgreSQL')
  await page
    .getByRole('textbox', { name: 'Experience', exact: true })
    .fill('Five years building payment APIs.')
  await page.getByRole('textbox', { name: 'Education', exact: true }).fill('BSc Computer Science')
  await page.getByRole('button', { name: 'Save profile' }).click()
  await expect(page.getByRole('status')).toContainText('Your profile has been saved.')
}
