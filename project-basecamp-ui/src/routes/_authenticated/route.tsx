import { createFileRoute, redirect } from '@tanstack/react-router'
import { AuthenticatedLayout } from '@/components/layout/authenticated-layout'

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: async ({ context, location }) => {
    // Wait for auth state to be determined by querying the session
    const sessionData = await context.queryClient.ensureQueryData({
      queryKey: ['whoami'],
      queryFn: async () => {
        try {
          const response = await fetch('/api/session/whoami')
          if (!response.ok) return { authenticated: false }
          return await response.json()
        } catch {
          return { authenticated: false }
        }
      },
    })

    // If not authenticated, redirect to sign-in with current location as redirect param
    if (!sessionData?.authenticated) {
      throw redirect({
        to: '/sign-in',
        search: {
          redirect: location.href,
        },
      })
    }
  },
  component: AuthenticatedLayout,
})
