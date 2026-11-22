import { NextResponse, type NextRequest } from 'next/server'
import { sessionProxy } from '@cv/auth/proxy'

// The shell owns the domain and routes each base path to its zone. This runs per request
// (next.config rewrites are fixed at build time), so the zone urls come from the runtime environment.
const zones = {
  profile: () => process.env.PROFILE_URL ?? 'http://localhost:3001',
  cv: () => process.env.CV_URL ?? 'http://localhost:3002',
}

const zonePath = /^\/(profile|cv)(\/|$)/

export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl
  const zone = zonePath.exec(pathname)?.[1] as keyof typeof zones | undefined
  // Each zone keeps its own session fresh, so a zone request is passed on untouched.
  if (zone) return NextResponse.rewrite(new URL(`${pathname}${search}`, zones[zone]()))

  return sessionProxy(request, undefined, { requireSignIn: pathname === '/' })
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
}
