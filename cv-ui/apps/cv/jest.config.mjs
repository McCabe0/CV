import { jestConfig } from '../../jest.shared.mjs'

export default await jestConfig({
  dir: import.meta.dirname,
  // The layout is wiring, covered by the playwright tests.
  collectCoverageFrom: ['proxy.ts', 'app/**/*.{ts,tsx}', '!app/**/*.d.ts', '!app/**/layout.tsx'],
})
