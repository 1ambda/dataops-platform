import { z } from 'zod'
import { createFileRoute, redirect } from '@tanstack/react-router'

const searchSchema = z.object({
  redirect: z.string().optional(),
})

// Simple forgot password component placeholder
function ForgotPassword() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="w-full max-w-md space-y-8">
        <div className="text-center">
          <h2 className="text-3xl font-bold">Forgot Password</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            Password reset functionality will be implemented here.
          </p>
        </div>
        <div className="mt-8">
          <a
            href="/sign-in"
            className="text-sm text-primary hover:underline"
          >
            Back to Sign In
          </a>
        </div>
      </div>
    </div>
  )
}

export const Route = createFileRoute('/(auth)/forgot-password')({
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
  component: ForgotPassword,
  validateSearch: searchSchema,
})