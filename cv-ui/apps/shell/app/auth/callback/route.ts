import type { NextRequest } from 'next/server'
import { callback } from '@cv/auth/handlers'

export const GET = (request: NextRequest) => callback(request)
