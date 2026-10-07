import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Renders [children] only for logged-in users with one of [roles]. Visitors who are not logged in go to the login
 * page, other roles to the home page. Renders nothing while the session is being checked, so the page never flashes.
 * This is only about what the UI shows; the backend enforces the same rules on every request.
 */
export function RequireRole({ roles, children }: { roles: string[]; children: ReactNode }) {
  const { user, authLoading } = useAuth()
  if (authLoading) return null
  if (!user) return <Navigate to="/login" replace />
  if (!roles.includes(user.role)) return <Navigate to="/" replace />
  return <>{children}</>
}
