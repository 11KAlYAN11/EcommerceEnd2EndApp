import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/*
 * WHY this config exists:
 *  - @vitejs/plugin-react: enables JSX transformation (JSX → React.createElement calls)
 *    and React Fast Refresh (hot reload that preserves component state)
 *  - server.port 5173: default Vite port
 *  - server.proxy: during dev, proxies /api calls to a backend on 8080
 *    This means we can write axios.get('/api/products') without hardcoding a host/port
 *    AND it avoids CORS in dev (both frontend and API appear same-origin to the browser)
 *
 * Phase 16 UI cutover (2026): target moved from the monolith (:8080) to the
 * API Gateway (:9000). Every route the frontend calls already lives under
 * /api/... on both sides, so this one line is the entire cutover -- no
 * component, no api/*.js file, no axios.js change needed. The monolith is
 * left running untouched at :8080 in case of rollback; just point this back
 * at 8080 to revert.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:9000',
        changeOrigin: true,
      }
    }
  }
})
