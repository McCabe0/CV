import type { NextRequest } from 'next/server'
import { sessionProxy } from '@cv/auth/proxy'

// Everything in this zone needs a signed in admin.
export const proxy = (request: NextRequest) =>
  sessionProxy(request, undefined, { requireSignIn: true, roles: ['ADMIN'] })

// With a base path, the catch all pattern needs a path after the base path, so the zone's root
// page must be listed on its own or it would skip the proxy.
export const config = {
  matcher: ['/', '/((?!_next/static|_next/image|favicon.ico).*)'],
}
