import { queryOptions, useQuery } from '@tanstack/react-query'

import { http } from '@/lib/http'
import { type SessionContext } from '@/_types/context.ts'

export function useSessionQuery() {
  return useQuery(
    queryOptions({
      queryKey: ['whoami'],
      queryFn: () => http.get('session/whoami').json<SessionContext>(),
      retry: false,
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
    })
  )
}
