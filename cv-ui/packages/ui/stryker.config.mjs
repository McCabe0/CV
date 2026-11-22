import { strykerConfig } from '../../stryker.shared.mjs'

export default strykerConfig({
  mutate: ['src/**/*.{ts,tsx}', '!src/index.ts'],
})
