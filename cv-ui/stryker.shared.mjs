import { fileURLToPath } from 'node:url'

/**
 * The stryker config for an app or package: mutation testing, weekly and report only, by
 * .github/workflows/mutation.yml. See decisions/mutation.md.
 * `mutate` is the same files as the jest config's `collectCoverageFrom`. Tests, types and fixtures are
 * left out here.
 *
 * Stryker runs from the app's or package's own directory, as an app's jest config only loads its
 * next.config.ts from there. It runs in place, not in a sandbox, as a sandbox would copy the directory
 * alone, and each jest config imports ../../jest.shared.mjs. It puts the files back when it ends.
 */
export function strykerConfig({ mutate }) {
  return {
    testRunner: 'jest',
    // By path, as stryker looks for its plugins beside itself, and pnpm keeps them apart.
    plugins: [fileURLToPath(import.meta.resolve('@stryker-mutator/jest-runner'))],
    jest: {
      projectType: 'custom',
      configFile: 'jest.config.mjs',
      // Each mutant runs only the tests that cover it, which would fail the coverage threshold.
      config: { collectCoverage: false },
    },
    mutate: [...mutate, '!**/*.test.{ts,tsx}', '!**/*.d.ts', '!**/fixtures.ts'],
    inPlace: true,
    coverageAnalysis: 'perTest',
    reporters: ['clear-text', 'progress-append-only', 'html', 'json'],
    timeoutMS: 10000,
  }
}
