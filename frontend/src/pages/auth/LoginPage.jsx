import { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { authApi } from '../../api/auth'
import { useAuth } from '../../context/AuthContext'
import './Auth.css'

/*
 * LoginPage — teaches:
 *   - Controlled inputs (value + onChange)
 *   - Async form submission pattern
 *   - e.preventDefault() — why it's required
 *   - Loading + error state management
 *   - useNavigate for redirect after login
 *   - Redirect-back-after-login (useLocation state)
 *   - finally block for cleanup
 */
export default function LoginPage() {
  // Individual state for each field — clear and explicit for 2-field forms
  const [email,    setEmail]    = useState('')
  const [password, setPassword] = useState('')
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState('')

  const { login }    = useAuth()
  const navigate     = useNavigate()
  const location     = useLocation()

  // Where did the user come from before being redirected to login?
  // ProtectedRoute passes: <Navigate to="/login" state={{ from: location }} />
  // Optional chaining (?.) — safe if no state was passed (direct visit to /login)
  const from = location.state?.from?.pathname || '/'

  async function handleSubmit(e) {
    // CRITICAL: Without this, browser submits the form as an HTTP request
    // → full page reload → all React state lost → SPA breaks
    e.preventDefault()

    setLoading(true)
    setError('')   // clear previous errors

    try {
      // authApi.login returns the full ApiResponse:
      // { success: true, data: { token: "eyJ..." }, message: "..." }
      const res = await authApi.login({ email, password })

      // Save token to context (→ localStorage) + decode user
      login(res.data.token)

      // Redirect back to where they were going, or home
      // replace: true — back button won't return to /login
      navigate(from, { replace: true })

    } catch (err) {
      // err.message was set by our axios response interceptor:
      // it extracts response.data.message or falls back to err.message
      setError(err.message)
    } finally {
      // ALWAYS runs — even if an error was thrown
      // Without finally: a network error leaves button disabled forever
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card card">
        <h2 className="auth-title">Welcome back</h2>
        <p className="auth-subtitle">Sign in to your account</p>

        {/* Error banner — only shown when error state is non-empty */}
        {error && <div className="error-msg">{error}</div>}

        <form onSubmit={handleSubmit}>
          {/*
           * Controlled input pattern:
           *   value={email}              → React state drives what's shown
           *   onChange={e => setEmail()} → every keystroke updates state
           * The input never "owns" its own value — React does.
           */}
          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
              autoFocus
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
            />
          </div>

          {/*
           * Button is disabled when:
           *   - loading (request in-flight — prevent double submit)
           *   - email or password is empty (basic guard)
           * This is possible ONLY because we use controlled inputs —
           * we always know the current field values in state.
           */}
          <button
            type="submit"
            className="btn btn-primary btn-full"
            disabled={loading || !email || !password}
          >
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>

        <p className="auth-footer">
          Don't have an account? <Link to="/register">Register</Link>
        </p>
      </div>
    </div>
  )
}
