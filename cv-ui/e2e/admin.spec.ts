import type { Page } from '@playwright/test'
import { expect, test } from './test'
import { expectAccessible, expectNoHorizontalScroll, signInOnKeycloak } from './support'

// The api reads each new board, and the job-worker crawls it, from wiremock playing the job boards
// (docker-compose.e2e.yml): any Lever board called played-... lists two jobs, and there is no Greenhouse
// board called no-such-board. See e2e/boards/mappings/played.json.

/** A company card, found by its name. */
const card = (page: Page, name: string) =>
  page.getByRole('listitem').filter({ has: page.getByRole('heading', { name, exact: true }) })

async function openAdmin(page: Page) {
  await page.goto('/admin')
  await signInOnKeycloak(page, 'admin')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Companies')
  await page.waitForLoadState('networkidle')
}

async function add(page: Page, name: string, board: string, token: string) {
  await page.getByRole('textbox', { name: 'Company name' }).fill(name)
  await page.getByRole('combobox', { name: 'Job board' }).selectOption(board)
  await page.getByRole('textbox', { name: 'Board token' }).fill(token)
  await page.getByRole('button', { name: 'Add company' }).click()
}

test('an admin adds a company, sees it crawled, crawls it again, and stops tracking it', async ({
  page,
}) => {
  // Each run adds its own, as the desktop and reflow runs share one database.
  const token = `played-${Date.now()}`
  const name = `Acme ${token.slice(-6)}`
  await openAdmin(page)
  await expectAccessible(page)
  await expectNoHorizontalScroll(page)

  await add(page, name, 'LEVER', token)
  await expect(page.getByRole('status').first()).toHaveText(
    `Added ${name}. Its jobs will show within a minute.`,
  )

  // Its first crawl was queued with it; the job-worker reads the played board within seconds.
  await expect(async () => {
    await page.reload()
    await expect(card(page, name)).toContainText('2 live jobs', { timeout: 1_000 })
  }).toPass({ timeout: 60_000 })
  await expect(card(page, name)).toContainText(`Lever: ${token}`)
  await expect(card(page, name)).toContainText('Last crawl: Worked')
  await expect(card(page, name)).toContainText('2 jobs listed, 2 new, 0 closed')
  await expectAccessible(page)
  await expectNoHorizontalScroll(page)

  await card(page, name)
    .getByRole('button', { name: `Crawl ${name} now` })
    .click()
  await expect(card(page, name).getByRole('status')).toContainText(`Crawling ${name} now.`)

  await card(page, name)
    .getByRole('button', { name: `Stop tracking ${name}` })
    .click()
  await expect(card(page, name)).toContainText('Not tracked since')
  await expect(card(page, name)).toContainText('0 live jobs')
  await expect(card(page, name).getByRole('button', { name: `Track ${name} again` })).toBeVisible()
})

test('an admin is told plainly when a board does not exist', async ({ page }) => {
  await openAdmin(page)

  await add(page, 'Nobody', 'GREENHOUSE', 'no-such-board')

  const problem = page.getByRole('heading', { name: 'There is a problem' }).locator('..')
  await expect(problem).toContainText(
    "There is no GREENHOUSE board called 'no-such-board'. Check the board token.",
  )
  await expect(page).toHaveTitle(/^Error: /)
  await expect(page.getByRole('textbox', { name: 'Board token' })).toHaveValue('no-such-board')
  await expectAccessible(page)
})

test('home leads an admin to the companies', async ({ page }) => {
  await page.goto('/')
  await signInOnKeycloak(page, 'admin')

  await page
    .getByRole('navigation', { name: 'Your areas' })
    .getByRole('link', { name: 'Admin' })
    .click()

  await expect(page).toHaveURL('/admin')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Companies')
})

test('a candidate cannot reach the admin area', async ({ page }) => {
  await page.goto('/admin')
  await signInOnKeycloak(page, 'candidate')

  await expect(page.getByText('Your account does not have access to the admin area.')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Admin' })).toHaveCount(0)
})
