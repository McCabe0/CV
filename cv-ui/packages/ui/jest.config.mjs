import { jestConfig } from '../../jest.shared.mjs'

export default await jestConfig({
  collectCoverageFrom: ['src/**/*.{ts,tsx}', '!src/**/*.d.ts', '!src/index.ts'],
})
