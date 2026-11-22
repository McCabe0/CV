import type { NextRequest } from 'next/server'
import { logout } from '@cv/auth/handlers'

export const POST = (request: NextRequest) => logout(request)
