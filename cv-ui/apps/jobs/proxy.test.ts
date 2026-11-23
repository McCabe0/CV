/** @jest-environment @stryker-mutator/jest-runner/jest-env/node */
import { NextRequest, NextResponse } from 'next/server'
import { sessionProxy } from '@cv/auth/proxy'
import { config, proxy } from './proxy'

jest.mock('@cv/auth/proxy', () => ({ sessionProxy: jest.fn() }))

describe('jobs zone proxy', () => {
  it('requires a signed in candidate, keeping the session fresh', async () => {
    const response = NextResponse.next()
    jest.mocked(sessionProxy).mockResolvedValue(response)
    const request = new NextRequest('http://localhost:3003/jobs')

    expect(await proxy(request)).toBe(response)
    expect(sessionProxy).toHaveBeenCalledWith(request, undefined, {
      requireSignIn: true,
      roles: ['CANDIDATE'],
    })
  })

  it('runs for the zone root and everything else except static files', () => {
    // With a base path, the catch all pattern needs a path after the base path, so the zone's
    // root page must be listed on its own or it would skip the proxy.
    expect(config.matcher).toEqual(['/', '/((?!_next/static|_next/image|favicon.ico).*)'])
  })
})
