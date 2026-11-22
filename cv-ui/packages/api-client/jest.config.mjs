import { jestConfig } from '../../jest.shared.mjs'

export default await jestConfig({
  testEnvironment: 'node',
  collectCoverageFrom: ['src/**/*.ts', '!src/**/*.d.ts', '!src/index.ts'],
})
