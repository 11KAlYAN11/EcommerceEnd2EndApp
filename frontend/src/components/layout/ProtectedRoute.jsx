import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

/*
 * ProtectedRoute — guards routes that need login.
 *
 * HOW it works:
 *   In App.jsx:  <Route element={<ProtectedRoute />}>
 *                  <Route path="/cart" element={<CartPage />} />
 *                </Route>
 *
 *   <Outlet /> renders the child route's component when authorized.
 *   If not logged in → redirect to /login.
 *
 * state={{ from: location }} — passes the attempted URL to LoginPage
 * so after login we can redirect back to where the user was going.
 * (We'll wire this up in Phase F-3)
 *
 * WHY not just check in CartPage itself?
 *   You'd have to add the check in EVERY protected page.
 *   This wrapper does it once for all routes nested inside it.
 *   Same reason you have Spring Security filter chain — centralized auth.
 */
import { useLocation } from 'react-router-dom'

export default function ProtectedRoute() {
  const { token } = useAuth()
  const location  = useLocation()

  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <Outlet />
}
