import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/*
 * WHY this config exists:
 *  - @vitejs/plugin-react: enables JSX transformation (JSX → React.createElement calls)
 *    and React Fast Refresh (hot reload that preserves component state)
 *  - server.port 5173: default Vite port
 *  - server.proxy: during dev, proxies /api calls to Spring Boot on 8080
 *    This means we can write axios.get('/api/products') without hardcoding localhost:8080
 *    AND it avoids CORS in dev (both frontend and API appear same-origin to the browser)
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})
