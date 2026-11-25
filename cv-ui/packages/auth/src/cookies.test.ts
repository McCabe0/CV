import { NextResponse } from 'next/server'
import { authConfig } from './config'
import {
  clearLoginCookie,
  clearSessionCookie,
  LOGIN_COOKIE,
  LOGIN_MAX_AGE_SECONDS,
  loginCookie,
  sessionCookie,
} from './cookies'
import { SESSION_COOKIE } from './session'

const config = (secureCookies: boolean) =>
  authConfig({
    KEYCLOAK_ISSUER: 'https://id.example.com/realms/skill2career',
    KEYCLOAK_CLIENT_ID: 'cv-ui',
    KEYCLOAK_CLIENT_SECRET: 'secret',
    APP_URL: secureCookies ? 'https://cv.example.com' : 'http://localhost:3000',
    SESSION_SECRET: 'x'.repeat(32),
  })

describe('the session cookie', () => {
  // No script can read it, it is sent on keycloak's redirect back but not on another site's post, it
  // reaches every zone, and it lasts as long as the browser keeps it, as the session inside it expires.
  it('is http only, lax, for the whole site, and has no max age', () => {
    expect(sessionCookie(config(true))).toEqual({
      httpOnly: true,
      secure: true,
      sameSite: 'lax',
      path: '/',
    })
  })

  it('is secure only where the site is served over https', () => {
    expect(sessionCookie(config(false)).secure).toBe(false)
  })

  it('is cleared for the whole site', () => {
    const response = NextResponse.next()

    clearSessionCookie(response)

    expect(response.cookies.get(SESSION_COOKIE)).toMatchObject({ value: '', path: '/', maxAge: 0 })
  })
})

describe('the login cookie', () => {
  it('is the same, but lasts only long enough to sign in', () => {
    expect(loginCookie(config(true))).toEqual({
      ...sessionCookie(config(true)),
      maxAge: LOGIN_MAX_AGE_SECONDS,
    })
    expect(LOGIN_MAX_AGE_SECONDS).toBe(600)
  })

  it('is cleared for the whole site', () => {
    const response = NextResponse.next()

    clearLoginCookie(response)

    expect(response.cookies.get(LOGIN_COOKIE)).toMatchObject({ value: '', path: '/', maxAge: 0 })
  })
})
