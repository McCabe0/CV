import { strykerConfig } from '../../stryker.shared.mjs'

export default strykerConfig({
  mutate: ['proxy.ts', 'app/**/*.{ts,tsx}', '!app/**/layout.tsx', '!app/auth/**/route.ts'],
})
