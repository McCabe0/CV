import type { Page } from '@playwright/test'
import { expect, test } from './test'
import { expectAccessible, expectNoHorizontalScroll, withProfile } from './support'

// The job-worker crawls wiremock playing the job boards here (docker-compose.e2e.yml): Monzo lists two
// Kotlin jobs, one in London and one remote, and an iOS job; every other board is empty. The fit summary
// is written by wiremock playing gemini. See e2e/boards/mappings and e2e/wiremock/mappings.

const backend = 'Senior Backend Engineer, Kotlin'
const platform = 'Platform Engineer'

/** The jobs listed on the page, by their headings. */
const jobs = (page: Page) => page.getByRole('listitem').getByRole('heading', { level: 2 })

/** The job's card, found by its title. */
const card = (page: Page, title: string) =>
  page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: title }) })

/**
 * Opens the jobs page once the job-worker's first crawl has saved the played boards' jobs. It crawls a few
 * seconds after it starts, so the first journey after the stack comes up may get there before it.
 */
async function openJobs(page: Page) {
  await page.goto('/jobs')
  await expect(async () => {
    await page.reload()
    await expect(page.getByRole('heading', { name: backend })).toBeVisible({ timeout: 1_000 })
  }).toPass({ timeout: 60_000 })
}

test('a candidate sees the jobs that ask for their skills, best first, and narrows them', async ({
  page,
}) => {
  await withProfile(page)
  await openJobs(page)

  // Both Kotlin jobs, the one asking for both skills first; never the iOS job, which asks for neither.
  await expect(jobs(page)).toHaveText([backend, platform])
  await expect(card(page, backend)).toContainText('Monzo · London')
  await expect(card(page, platform)).toContainText('Monzo · Remote, UK · Remote')
  await expect(card(page, backend).getByRole('link', { name: /Apply on Monzo/ })).toHaveAttribute(
    'href',
    'https://example.com/monzo/jobs/7001',
  )
  await expectAccessible(page)
  await expectNoHorizontalScroll(page)

  await page.getByRole('checkbox', { name: 'Remote only' }).check()
  await page.getByRole('button', { name: 'Show jobs' }).click()
  await expect(page).toHaveURL(/remoteOnly=on/)
  await expect(jobs(page)).toHaveText([platform])

  await page.getByRole('checkbox', { name: 'Remote only' }).uncheck()
  await page.getByRole('textbox', { name: 'Location' }).fill('London')
  await page.getByRole('button', { name: 'Show jobs' }).click()
  await expect(jobs(page)).toHaveText([backend])
})

test('a candidate asks why a job fits, and has a CV written for it', async ({ page }) => {
  await withProfile(page)
  await openJobs(page)

  // The summary is written in the cv-worker, through kafka; the card checks back until it is there.
  await card(page, backend).getByRole('button', { name: 'Why it fits' }).click()
  await expect(card(page, backend)).toContainText(
    'The job asks for Kotlin and PostgreSQL, and you have built payment APIs with both.',
    { timeout: 30_000 },
  )
  await expectAccessible(page)

  await card(page, backend)
    .getByRole('link', { name: /Write a CV for this job/ })
    .click()
  await expect(page).toHaveURL(/\/cv\?job=\d+$/)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('A CV for one job')
  await page.getByRole('button', { name: 'Write a CV for this job' }).click()

  // The same interview, draft and review as a general CV, from the played gemini.
  await expect(page).toHaveURL(/\/cv\/requests\/\d+$/)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('A few questions', {
    timeout: 30_000,
  })
  await page.getByRole('button', { name: 'Send answers' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Your CV', { timeout: 30_000 })
  await expect(page.getByRole('heading', { level: 2, name: 'Casey Candidate' })).toBeVisible()
})
