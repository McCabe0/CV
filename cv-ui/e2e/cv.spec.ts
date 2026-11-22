import type { Page } from '@playwright/test'
import { expect, test } from './test'
import { expectAccessible, expectNoHorizontalScroll, signInOnKeycloak } from './support'

// The cv-worker's LLM is wiremock playing gemini here (docker-compose.e2e.yml), so every run gets the
// same question, draft and review: e2e/wiremock/mappings.

/** Signs in, and saves a profile, so the CV has something to be written from. */
async function withProfile(page: Page) {
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

test('a candidate asks for a CV, answers the interviewer, and watches it written', async ({
  page,
}) => {
  await withProfile(page)

  await page.getByRole('link', { name: 'Write your CV' }).click()
  await expect(page).toHaveURL('/cv')
  await expectAccessible(page)
  await page.getByRole('button', { name: 'Write my CV' }).click()

  // The interviewer runs in the cv-worker, through kafka; the page follows it, then shows its question.
  await expect(page).toHaveURL(/\/cv\/requests\/\d+$/)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('A few questions', {
    timeout: 30_000,
  })
  await expectAccessible(page)
  await expectNoHorizontalScroll(page)
  await page
    .getByRole('textbox', { name: 'By how much did you cut settlement time? (optional)' })
    .fill('By 40%')
  await page.getByRole('button', { name: 'Send answers' }).click()

  // The writer and critic, then the finished CV.
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Your CV', { timeout: 30_000 })
  await expect(page.getByRole('heading', { level: 2, name: 'Casey Candidate' })).toBeVisible()
  await expect(page.getByText('Backend engineer who cut settlement time by 40%.')).toBeVisible()
  await expect(page.getByText('Draft 1: 92 out of 100, approved')).toBeVisible()
  await expectAccessible(page)
  await expectNoHorizontalScroll(page)
})

test('a candidate without a profile is asked to save one first', async ({ page }) => {
  // The realm's admin is a candidate too, and no journey saves a profile for them.
  await page.goto('/cv')
  await signInOnKeycloak(page, 'admin')

  await page.getByRole('button', { name: 'Write my CV' }).click()

  // In main: next adds an empty alert of its own, to announce page changes.
  await expect(page.getByRole('main').getByRole('alert')).toContainText(
    'Save your profile before asking for a CV.',
  )
  await expect(page.getByRole('link', { name: 'Go to your profile' })).toHaveAttribute(
    'href',
    '/profile',
  )
})

test("one candidate never sees another's CV request", async ({ page, browser }) => {
  await withProfile(page)
  await page.goto('/cv')
  await page.getByRole('button', { name: 'Write my CV' }).click()
  await expect(page).toHaveURL(/\/cv\/requests\/\d+$/)
  const theirs = new URL(page.url()).pathname

  const other = await browser.newPage()
  await other.goto(theirs)
  await signInOnKeycloak(other, 'candidate-other')

  await expect(other.getByRole('heading', { level: 1 })).toHaveText('Page not found')
  await other.close()
})
