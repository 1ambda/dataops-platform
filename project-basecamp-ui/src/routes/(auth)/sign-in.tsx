import { z } from 'zod'
import { createFileRoute, redirect } from '@tanstack/react-router'
import { SignIn } from '@/features/auth/sign-in'

const searchSchema = z.object({
  redirect: z.string().optional(),
})

export const Route = createFileRoute('/(auth)/sign-in')({
  beforeLoad: async ({ context }) => {
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

    // If already authenticated, redirect to home
    if (sessionData?.authenticated) {
      throw redirect({
        to: '/',
      })
    }
  },
  component: SignIn,
  validateSearch: searchSchema,
})
