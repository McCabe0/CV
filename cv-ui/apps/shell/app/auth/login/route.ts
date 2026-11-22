import type { NextRequest } from 'next/server'
import { login } from '@cv/auth/handlers'

export const GET = (request: NextRequest) => login(request)
