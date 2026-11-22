import { jestConfig } from '../../jest.shared.mjs'

export default await jestConfig({
  dir: import.meta.dirname,
  // Layouts and the one line auth routes are wiring, covered by the playwright tests.
  collectCoverageFrom: [
    'proxy.ts',
    'app/**/*.{ts,tsx}',
    '!app/**/*.d.ts',
    '!app/**/layout.tsx',
    '!app/auth/**/route.ts',
  ],
})
