import { expect, test } from './test'
import { expectAccessible, expectNoHorizontalScroll, signInOnKeycloak } from './support'

test('a signed out user is sent to sign in, then back to the page they asked for', async ({
  page,
}) => {
  await page.goto('/profile')

  await signInOnKeycloak(page, 'candidate')

  await expect(page).toHaveURL('/profile')
  await expect(page).toHaveTitle('Your profile — Skill2Career')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Your profile')
  await expect(
    page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Your profile' }),
  ).toHaveAttribute('aria-current', 'true')
  await expect(page.getByRole('banner')).toContainText('Signed in as Casey Candidate')
  await expectAccessible(page)
  await expectNoHorizontalScroll(page)
})

test('home shows the signed out what it does, and signs them in', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    'A CV written for you, and the jobs it fits',
  )
  await expectAccessible(page)
  await expectNoHorizontalScroll(page)

  await page.getByRole('link', { name: 'Sign in' }).click()
  await signInOnKeycloak(page, 'candidate')

  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Casey Candidate')
})

test('home lists what the user can use, and leads to it', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: 'Sign in' }).click()
  await signInOnKeycloak(page, 'candidate')

  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Casey Candidate')
  await expectAccessible(page)
  await expectNoHorizontalScroll(page)

  await page
    .getByRole('navigation', { name: 'Your areas' })
    .getByRole('link', { name: 'Your profile' })
    .click()

  await expect(page).toHaveURL('/profile')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Your profile')
})

test('the main navigation moves between the areas, and back home', async ({ page }) => {
  await page.goto('/profile')
  await signInOnKeycloak(page, 'candidate')
  const nav = page.getByRole('navigation', { name: 'Main' })

  await nav.getByRole('link', { name: 'Jobs for you' }).click()
  await expect(page).toHaveURL('/jobs')
  await expect(nav.getByRole('link', { name: 'Jobs for you' })).toHaveAttribute(
    'aria-current',
    'true',
  )

  await nav.getByRole('link', { name: 'Your CV' }).click()
  await expect(page).toHaveURL('/cv')

  await nav.getByRole('link', { name: 'Home' }).click()
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Welcome, Casey Candidate')
  // A candidate is not offered the admin area.
  await expect(nav.getByRole('link', { name: 'Admin' })).toHaveCount(0)
  await expectNoHorizontalScroll(page)
})

test('signing out ends the session, so the profile needs a sign in again', async ({ page }) => {
  await page.goto('/profile')
  await signInOnKeycloak(page, 'candidate')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Your profile')

  await page.getByRole('button', { name: 'Sign out' }).click()
  await page.goto('/profile')

  // Keycloak's session ended too, so it asks for the password again rather than signing straight back in.
  await expect(page).toHaveURL(/^http:\/\/localhost:8180\/realms\/skill2career\//)
  await expect(page.getByLabel('Password', { exact: true })).toBeVisible()
})

test('a page that does not exist says so, in the service', async ({ page }) => {
  const response = await page.goto('/no-such-page')

  expect(response?.status()).toBe(404)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Page not found')
  await expectAccessible(page)
})
