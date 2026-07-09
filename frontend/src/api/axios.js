import axios from 'axios'

/*
 * WHY a custom Axios instance instead of plain fetch?
 *
 * 1. Base URL — write '/api/products' not 'http://localhost:8080/api/products'
 *    In production you change baseURL in ONE place, not in every component.
 *
 * 2. Request interceptor — automatically attaches JWT token to every request.
 *    Without this: every API call needs:
 *      headers: { Authorization: `Bearer ${token}` }
 *    With this: write nothing — the interceptor handles it automatically.
 *
 * 3. Response interceptor — centralized error handling.
 *    401 Unauthorized → clear token → redirect to login
 *    You don't handle auth expiry in 50 different components.
 *
 * Interceptor = middleware for HTTP calls (same concept as Spring's OncePerRequestFilter)
 *
 * baseURL '/api': Vite's dev proxy forwards /api/* → http://localhost:8080/api/*
 * So in dev: no CORS, no hardcoded port.
 * In production: set VITE_API_URL env var → build replaces it.
 */

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10000,  // 10s — fail fast rather than hang forever
})

// REQUEST interceptor — runs before every outgoing request
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// RESPONSE interceptor — runs on every response (success or error)
api.interceptors.response.use(
  // Success (2xx): unwrap the ApiResponse wrapper our Spring Boot returns
  // Our backend always returns: { success, message, data, timestamp }
  // We want just `data` in our components, not the wrapper
  (response) => response.data,

  // Error (4xx, 5xx, network):
  (error) => {
    if (error.response?.status === 401) {
      // Token expired or invalid → force logout
      localStorage.removeItem('token')
      // Only redirect if not already on auth pages
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login'
      }
    }
    // Bubble up the error with a friendly message
    const message = error.response?.data?.message || error.message || 'Something went wrong'
    return Promise.reject(new Error(message))
  }
)

export default api
