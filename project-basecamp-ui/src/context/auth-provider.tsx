import React, { createContext, type ReactNode, useMemo } from 'react'
import { useSessionQuery } from '@/hooks/use-session-query.tsx'

// AuthContext 타입 정의
interface AuthContextType {
  auth: boolean | undefined
  userId: string | undefined
  email: string | undefined
  username: string | undefined
  roles: string[] | undefined
}

interface AuthProviderProps {
  children: ReactNode
}

// AuthContext 생성 (export 추가)
export const AuthContext = createContext<AuthContextType | undefined>(undefined)

// AuthProvider
export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const sessionQuery = useSessionQuery()
  const value = useMemo(
    () => ({
      auth: sessionQuery.isSuccess && sessionQuery.data?.authenticated,
      userId: sessionQuery.data?.userId,
      email: sessionQuery.data?.email,
      username: sessionQuery.data?.email?.split('@')[0],
      roles: sessionQuery.data?.roles,
    }),
    [sessionQuery.data, sessionQuery.isSuccess]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}