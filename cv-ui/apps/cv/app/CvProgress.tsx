'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { BASE_PATH } from './basePath'
import { statusLine, type CvRequest } from './cvRequest'
import { Rounds } from './Rounds'

/**
 * Follows a request while the agents work on it. Each event is the whole request, from this zone's
 * stream (which carries the token the browser never sees). Once the request needs the candidate, or is
 * finished, the page reloads to show the questions or the CV.
 */
export function CvProgress({ initial }: { initial: CvRequest }) {
  const [request, setRequest] = useState(initial)
  const router = useRouter()

  useEffect(() => {
    const source = new EventSource(`${BASE_PATH}/requests/${initial.id}/events`)
    source.addEventListener('progress', (event) => {
      const next = JSON.parse((event as MessageEvent<string>).data) as CvRequest
      setRequest(next)
      if (next.status !== 'REQUESTED' && next.status !== 'WRITING') {
        source.close()
        router.refresh()
      }
    })
    return () => source.close()
  }, [initial.id, router])

  return (
    <>
      <p role="status">{statusLine(request)}</p>
      <Rounds rounds={request.rounds} />
    </>
  )
}
