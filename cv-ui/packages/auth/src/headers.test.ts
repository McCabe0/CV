import { contentSecurityPolicy, SECURITY_HEADERS } from './headers'

const directives = (policy: string) =>
  Object.fromEntries(policy.split('; ').map((directive) => [directive.split(' ')[0], directive]))

describe('contentSecurityPolicy', () => {
  it("only runs scripts and styles with this response's nonce, and nothing from elsewhere", () => {
    const policy = directives(contentSecurityPolicy('abc', ['https://keycloak.example']))

    expect(policy).toEqual({
      'default-src': "default-src 'self'",
      'script-src': "script-src 'self' 'nonce-abc' 'strict-dynamic'",
      'style-src': "style-src 'self' 'nonce-abc'",
      'img-src': "img-src 'self' data:",
      'font-src': "font-src 'self'",
      'connect-src': "connect-src 'self'",
      'object-src': "object-src 'none'",
      'base-uri': "base-uri 'self'",
      'form-action': "form-action 'self' https://keycloak.example",
      'frame-ancestors': "frame-ancestors 'none'",
    })
  })

  it('posts forms to this site only, unless told otherwise', () => {
    expect(directives(contentSecurityPolicy('abc'))['form-action']).toBe("form-action 'self'")
  })

  it("allows eval in development only, as next's hot reload needs it", () => {
    expect(directives(contentSecurityPolicy('abc', [], true))['script-src']).toBe(
      "script-src 'self' 'nonce-abc' 'strict-dynamic' 'unsafe-eval'",
    )
  })
})

describe('SECURITY_HEADERS', () => {
  it('refuses framing and sniffing, and turns off powerful features', () => {
    expect(Object.fromEntries(SECURITY_HEADERS.map(({ key, value }) => [key, value]))).toEqual({
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'DENY',
      'Referrer-Policy': 'strict-origin-when-cross-origin',
      'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
      'Cross-Origin-Resource-Policy': 'same-origin',
      'Cross-Origin-Opener-Policy': 'same-origin',
    })
  })
})
