// No next imports here: each app's next.config loads this.

/** Headers for every response, set in each app's next.config. The content security policy needs a nonce, so the proxy sets it. */
export const SECURITY_HEADERS = [
  { key: 'X-Content-Type-Options', value: 'nosniff' },
  // The content security policy's frame-ancestors does this too, for browsers that support it.
  { key: 'X-Frame-Options', value: 'DENY' },
  { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
  {
    key: 'Permissions-Policy',
    value: 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
  },
  { key: 'Cross-Origin-Resource-Policy', value: 'same-origin' },
  { key: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
  // A page loads nothing from another origin, so it can refuse anything that has not opted in. With
  // the opener policy, it is cross origin isolated. Found by the ZAP scan (decisions/zap.md).
  { key: 'Cross-Origin-Embedder-Policy', value: 'require-corp' },
]

/**
 * The content security policy for a page. Only scripts and styles carrying the nonce run, and
 * next adds it to its own when it finds the policy on the request. Forms post to this site, and
 * to `formActions` (eg keycloak, as signing out posts here then redirects there).
 */
export function contentSecurityPolicy(
  nonce: string,
  formActions: string[] = [],
  development = false,
): string {
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${development ? " 'unsafe-eval'" : ''}`,
    `style-src 'self' 'nonce-${nonce}'`,
    "img-src 'self' data:",
    "font-src 'self'",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    ["form-action 'self'", ...formActions].join(' '),
    "frame-ancestors 'none'",
  ].join('; ')
}
