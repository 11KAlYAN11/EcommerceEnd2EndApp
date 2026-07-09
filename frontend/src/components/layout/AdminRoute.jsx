import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

/*
 * AdminRoute — double guard:
 *   1. Must be logged in (token exists)
 *   2. Must have ROLE_ADMIN
 *
 * Regular users hitting /admin → redirected to /
 * Not logged in → redirected to /login
 */
export default function AdminRoute() {
  const { token, isAdmin } = useAuth()

  if (!token)   return <Navigate to="/login" replace />
  if (!isAdmin) return <Navigate to="/" replace />

  return <Outlet />
}
