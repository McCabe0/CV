import type { NextConfig } from 'next'
import { loadEnvConfig } from '@next/env'
import path from 'node:path'
import { SECURITY_HEADERS } from '@cv/auth/headers'

// Every app shares the settings in the workspace root's .env files.
loadEnvConfig(path.join(import.meta.dirname, '../..'), process.env.NODE_ENV !== 'production')

import { BASE_PATH } from './app/basePath'

// A zone: served by the shell under its base path.
const nextConfig: NextConfig = {
  basePath: BASE_PATH,
  output: 'standalone',
  // Says nothing about the server. The content security policy is set per request by the proxy.
  poweredByHeader: false,
  headers: async () => [{ source: '/:path*', headers: SECURITY_HEADERS }],
  // The build checks the app's types only. pnpm typecheck checks the tests.
  typescript: { tsconfigPath: 'tsconfig.build.json' },
  outputFileTracingRoot: path.join(import.meta.dirname, '../..'),
  transpilePackages: ['@cv/ui', '@cv/auth', '@cv/api-client'],
}

export default nextConfig
