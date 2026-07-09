import { createContext, useContext, useState, useEffect } from 'react'

/*
 * WHY Context API?
 *
 * Problem — "prop drilling":
 *   App → Navbar → UserMenu → Avatar (needs: user, logout)
 *   Without context, you pass user/logout as props through EVERY level.
 *   If you add a level in between, every intermediate component has to forward it.
 *
 * Solution — Context:
 *   One place stores the value (AuthContext.Provider)
 *   Any component anywhere in the tree reads it with useContext(AuthContext)
 *   No passing through intermediate components
 *
 * What we store in AuthContext:
 *   - user: { email, role } decoded from JWT, or null if not logged in
 *   - token: raw JWT string (sent in Authorization header)
 *   - login(token): called after successful login → save token, decode user
 *   - logout(): clear everything → redirect to login
 *
 * WHY localStorage for JWT?
 *   localStorage persists across browser refreshes. Without it, every F5
 *   logs the user out. Alternative: httpOnly cookies (more secure, but
 *   requires server support and more setup).
 *   For a learning project, localStorage is the standard approach to teach.
 */

const AuthContext = createContext(null)

// Decode the JWT payload (middle part) without verifying signature
// Verification happens on the server — we just read the claims client-side
function decodeToken(token) {
  try {
    const payload = token.split('.')[1]              // Header.PAYLOAD.Signature
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decoded)
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [token, setToken]   = useState(() => localStorage.getItem('token'))
  const [user,  setUser]    = useState(() => {
    const t = localStorage.getItem('token')
    return t ? decodeToken(t) : null
  })

  // Called after login/register API returns a token
  function login(newToken) {
    localStorage.setItem('token', newToken)
    setToken(newToken)
    setUser(decodeToken(newToken))
  }

  function logout() {
    localStorage.removeItem('token')
    setToken(null)
    setUser(null)
  }

  // Check if JWT is expired on mount
  // sub = subject (email), exp = expiry timestamp (seconds since epoch)
  useEffect(() => {
    if (user && user.exp * 1000 < Date.now()) {
      logout()
    }
  }, [])

  // JWT payload has `roles` array e.g. ["ROLE_USER"] or ["ROLE_USER","ROLE_ADMIN"]
  const isAdmin = Array.isArray(user?.roles)
    ? user.roles.includes('ROLE_ADMIN')
    : user?.role === 'ROLE_ADMIN'

  return (
    <AuthContext.Provider value={{ token, user, isAdmin, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

// Custom hook — useAuth() is cleaner than useContext(AuthContext) everywhere
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
