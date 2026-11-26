import * as client from 'openid-client'
import type { AuthConfig } from './config'
import type { Session } from './session'
import { userFromAccessToken } from './user'

/** The url this server should call: the internal address for keycloak, when one is set. */
const serverUrl = (config: AuthConfig, url: string) =>
  config.internalUrl && url.startsWith(config.issuer.origin)
    ? `${config.internalUrl.origin}${url.slice(config.issuer.origin.length)}`
    : url

const discoveryOptions = (config: AuthConfig): client.DiscoveryRequestOptions | undefined => {
  if (!config.allowHttp && !config.internalUrl) return undefined
  return {
    ...(config.allowHttp && { execute: [client.allowInsecureRequests] }),
    ...(config.internalUrl && {
      [client.customFetch]: (url: string, options: client.CustomFetchOptions) =>
        fetch(serverUrl(config, url), options as RequestInit),
    }),
  }
}

const discovered = new Map<string, Promise<client.Configuration>>()

/** Keycloak's endpoints, discovered once per issuer. A failed discovery is retried next time. */
function keycloak(config: AuthConfig): Promise<client.Configuration> {
  const key = `${config.issuer}|${config.clientId}`
  let configuration = discovered.get(key)
  if (!configuration) {
    configuration = client.discovery(
      config.issuer,
      config.clientId,
      config.clientSecret,
      undefined,
      discoveryOptions(config),
    )
    configuration.catch(() => discovered.delete(key))
    discovered.set(key, configuration)
  }
  return configuration
}

export const callbackUrl = (config: AuthConfig) => new URL('/auth/callback', config.appUrl)

export type LoginState = { verifier: string; state: string }

/** Where to send the user to sign in, and what must be kept to check the callback. */
export async function beginLogin(config: AuthConfig): Promise<LoginState & { url: URL }> {
  const verifier = client.randomPKCECodeVerifier()
  const state = client.randomState()
  const url = client.buildAuthorizationUrl(await keycloak(config), {
    redirect_uri: callbackUrl(config).href,
    scope: 'openid',
    code_challenge: await client.calculatePKCECodeChallenge(verifier),
    code_challenge_method: 'S256',
    state,
  })
  return { url, verifier, state }
}

const nowSeconds = () => Math.floor(Date.now() / 1000)

// What the code and refresh grants both return. Since openid-client 6.9 each brands its result differently.
type Tokens = client.TokenEndpointResponse & client.TokenEndpointResponseHelpers

const sessionFrom = (tokens: Tokens, refreshToken: string | undefined): Session => {
  if (!refreshToken) throw new Error('Keycloak did not return a refresh token')
  return {
    user: userFromAccessToken(tokens.access_token),
    accessToken: tokens.access_token,
    refreshToken,
    expiresAt: nowSeconds() + (tokens.expiresIn() ?? 0),
  }
}

export async function completeLogin(
  config: AuthConfig,
  callback: URL,
  { verifier, state }: LoginState,
): Promise<Session> {
  const tokens = await client.authorizationCodeGrant(await keycloak(config), callback, {
    pkceCodeVerifier: verifier,
    expectedState: state,
  })
  return sessionFrom(tokens, tokens.refresh_token)
}

export async function refreshSession(config: AuthConfig, session: Session): Promise<Session> {
  const tokens = await client.refreshTokenGrant(await keycloak(config), session.refreshToken)
  return sessionFrom(tokens, tokens.refresh_token ?? session.refreshToken)
}

/** Ends the keycloak session from the server, so no id token needs to be kept in the cookie. */
export async function endSession(config: AuthConfig, session: Session): Promise<void> {
  const endpoint = (await keycloak(config)).serverMetadata().end_session_endpoint
  if (!endpoint) throw new Error('Keycloak has no logout endpoint')

  const response = await fetch(serverUrl(config, endpoint), {
    method: 'POST',
    body: new URLSearchParams({
      client_id: config.clientId,
      client_secret: config.clientSecret,
      refresh_token: session.refreshToken,
    }),
  })
  if (!response.ok) throw new Error(`Keycloak logout failed: ${response.status}`)
}
