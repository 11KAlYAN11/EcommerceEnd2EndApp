import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../../api/auth'
import { useAuth } from '../../context/AuthContext'
import './Auth.css'

/*
 * RegisterPage — teaches Option B: single form object for multiple fields.
 *
 * WHY form object instead of individual useState?
 *   5 fields × 2 states (value + setter) = 10 useState calls.
 *   One form object = 1 useState, cleaner update pattern.
 *
 * Spread operator in state update:
 *   setForm(prev => ({ ...prev, [field]: value }))
 *   - prev: current form object
 *   - ...prev: copy all existing keys
 *   - [field]: value: override just the changed field
 *   This is IMMUTABLE update — we never mutate the existing object.
 *   React needs a NEW object reference to detect the change and re-render.
 */
export default function RegisterPage() {
  const [form, setForm] = useState({
    firstName: '',
    lastName:  '',
    email:     '',
    password:  '',
    phone:     '',
  })
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState('')

  const { login }  = useAuth()
  const navigate   = useNavigate()

  // Generic change handler — works for ALL fields
  // e.target.name must match the key in our form object
  function handleChange(e) {
    const { name, value } = e.target
    setForm(prev => ({ ...prev, [name]: value }))
    //              ↑ spread copies everything
    //                           ↑ computed property: [name] → e.g. "email"
  }

  async function handleSubmit(e) {
    e.preventDefault()

    // Client-side guard: password length
    if (form.password.length < 6) {
      setError('Password must be at least 6 characters')
      return  // stop here — don't call the API
    }

    setLoading(true)
    setError('')

    try {
      // Register returns a token just like login
      const res = await authApi.register(form)
      login(res.data.token)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card card">
        <h2 className="auth-title">Create account</h2>
        <p className="auth-subtitle">Start shopping today</p>

        {error && <div className="error-msg">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-row">
            <div className="form-group">
              <label>First Name</label>
              <input
                name="firstName"
                value={form.firstName}
                onChange={handleChange}
                placeholder="John"
                required
                autoFocus
              />
            </div>
            <div className="form-group">
              <label>Last Name</label>
              <input
                name="lastName"
                value={form.lastName}
                onChange={handleChange}
                placeholder="Doe"
                required
              />
            </div>
          </div>

          <div className="form-group">
            <label>Email</label>
            <input
              type="email"
              name="email"
              value={form.email}
              onChange={handleChange}
              placeholder="you@example.com"
              required
            />
          </div>

          <div className="form-group">
            <label>Password <span className="text-muted">(min 6 characters)</span></label>
            <input
              type="password"
              name="password"
              value={form.password}
              onChange={handleChange}
              placeholder="••••••••"
              required
            />
          </div>

          <div className="form-group">
            <label>Phone <span className="text-muted">(optional)</span></label>
            <input
              type="tel"
              name="phone"
              value={form.phone}
              onChange={handleChange}
              placeholder="9876543210"
            />
          </div>

          <button
            type="submit"
            className="btn btn-primary btn-full"
            disabled={loading || !form.firstName || !form.email || !form.password}
          >
            {loading ? 'Creating account…' : 'Create Account'}
          </button>
        </form>

        <p className="auth-footer">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </div>
  )
}
