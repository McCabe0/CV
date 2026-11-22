import nextJest from 'next/jest.js'
import path from 'node:path'

// Dependencies that only ship es modules, which jest must transform like our own code.
const esmOnly = ['jose', 'openid-client', 'oauth4webapi']

/**
 * The jest config for an app or package. Coverage below 90% fails the build. See decisions/ui.md.
 * `dir` is the Next app's directory. Packages that are not Next apps leave it out.
 */
export async function jestConfig({ dir, ...config }) {
  const createJestConfig = nextJest(dir ? { dir } : undefined)
  const resolved = await createJestConfig({
    testEnvironment: 'jsdom',
    clearMocks: true,
    setupFilesAfterEnv: [path.join(import.meta.dirname, 'jest.setup.ts')],
    collectCoverage: true,
    coverageProvider: 'v8',
    coverageReporters: ['text-summary', 'lcov', 'json-summary'],
    coverageThreshold: {
      global: { lines: 90, branches: 90, functions: 90, statements: 90 },
    },
    ...config,
  })()

  return {
    ...resolved,
    transformIgnorePatterns: [
      `/node_modules/(?!(\.pnpm/[^/]+/node_modules/)?(${esmOnly.join('|')})/)`,
    ],
  }
}
