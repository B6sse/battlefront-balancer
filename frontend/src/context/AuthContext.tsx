import { createContext, useContext, useEffect, useState, useCallback } from 'react'
import type { ReactNode } from 'react'
import { apiLogin, apiLogout, apiMe } from '../api/auth'
import type { LoginResult, User } from '../api/auth'

interface AuthContextType {
  user: User | null
  authLoading: boolean
  /** Password step. Sets the user when no two-factor step is needed; otherwise returns the next step. */
  login: (username: string, password: string) => Promise<LoginResult>
  /** Sets the user after a successful two-factor step. */
  completeLogin: (user: User) => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [authLoading, setAuthLoading] = useState(true)

  useEffect(() => {
    apiMe()
      .then(setUser)
      .finally(() => setAuthLoading(false))
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const result = await apiLogin(username, password)
    if (result.status === 'OK' && result.user) setUser(result.user)
    return result
  }, [])

  const completeLogin = useCallback((u: User) => setUser(u), [])

  const logout = useCallback(async () => {
    await apiLogout()
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, authLoading, login, completeLogin, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
