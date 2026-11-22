import { strykerConfig } from '../../stryker.shared.mjs'

export default strykerConfig({
  mutate: ['src/**/*.ts', '!src/index.ts'],
})
