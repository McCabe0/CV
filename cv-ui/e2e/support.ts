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
