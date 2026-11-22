import { expect, test as base } from '@playwright/test'

export { expect }

/**
 * Playwright's test, failing any test where the browser blocked something on our pages for the
 * content security policy: a script, style or form next or our code needs. Keycloak's pages
 * have their own policy, so they are left to keycloak.
 */
export const test = base.extend<{ contentSecurityPolicy: void }>({
  contentSecurityPolicy: [
    async ({ page }, use) => {
      const violations: string[] = []
      page.on('console', (message) => {
        const blocked =
          message.type() === 'error' && message.text().includes('Content Security Policy')
        if (blocked && !page.url().includes(':8180/'))
          violations.push(`${page.url()}: ${message.text()}`)
      })
      await use()
      expect(violations, 'content security policy violations').toEqual([])
    },
    { auto: true },
  ],
})
