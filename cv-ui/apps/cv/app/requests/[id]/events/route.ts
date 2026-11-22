import { getSession } from '@cv/auth/server'

// A live stream: never cached, never built ahead.
export const dynamic = 'force-dynamic'

type Context = { params: Promise<{ id: string }> }

/**
 * The api's progress stream for one request, passed through this zone with the candidate's token, so the
 * token never reaches the browser. Closing the page closes the call to the api too.
 */
export async function GET(request: Request, { params }: Context) {
  const { id } = await params
  if (!/^\d+$/.test(id)) return new Response(null, { status: 404 })

  const session = await getSession()
  if (!session) return new Response(null, { status: 401 })

  const upstream = await fetch(`${process.env.API_URL}/cv-requests/${id}/events`, {
    headers: { Authorization: `Bearer ${session.accessToken}`, Accept: 'text/event-stream' },
    cache: 'no-store',
    signal: request.signal,
  })
  if (!upstream.ok || !upstream.body) return new Response(null, { status: upstream.status })

  return new Response(upstream.body, {
    headers: { 'content-type': 'text/event-stream', 'cache-control': 'no-cache, no-transform' },
  })
}
