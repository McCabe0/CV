import { defineConfig, globalIgnores } from 'eslint/config'
import nextVitals from 'eslint-config-next/core-web-vitals'
import nextTs from 'eslint-config-next/typescript'
import jsxA11y from 'eslint-plugin-jsx-a11y'

export default defineConfig([
  ...nextVitals,
  ...nextTs,
  // next already registers the jsx-a11y plugin, so only its strict rules are added here.
  { rules: jsxA11y.flatConfigs.strict.rules },
  {
    settings: { next: { rootDir: ['apps/*/'] } },
  },
  globalIgnores([
    '**/node_modules/**',
    '**/.next/**',
    '**/coverage/**',
    'playwright-report/**',
    'test-results/**',
    '**/next-env.d.ts',
  ]),
])
