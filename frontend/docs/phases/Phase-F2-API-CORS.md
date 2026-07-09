# Phase F-2 — API Layer & CORS

---

## 1. The Problem: Same-Origin Policy

When your React app (port 5173) makes a request to Spring Boot (port 8080),
the browser sees two DIFFERENT origins:

```
http://localhost:5173  ← React (origin A)
http://localhost:8080  ← Spring Boot (origin B)
```

Origin = protocol + host + port. All three must match for "same-origin".

The browser enforces **Same-Origin Policy (SOP)**:
> "A page from origin A cannot read responses from origin B."

This is a BROWSER security feature — not a server restriction.
The server receives and processes the request. The browser intercepts
the response and throws it away if the server didn't explicitly allow it.

```
React (5173) → request → Spring Boot (8080)  ✅ request goes through
Spring Boot (8080) → response → Browser       ❌ browser blocks it
                                               (no CORS headers in response)
```

---

## 2. The Fix: CORS Headers

The server adds special headers to its response:

```
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, PATCH
Access-Control-Allow-Headers: Authorization, Content-Type
```

When the browser sees these, it allows the JS code to read the response.

**Who adds these headers?** Spring Boot — via Spring Security's CORS configuration.

---

## 3. Preflight Requests (OPTIONS)

For "non-simple" requests (POST with JSON body, requests with Authorization header),
the browser sends an **OPTIONS request first** — called a "preflight":

```
Browser → OPTIONS /api/auth/login  (asking: "can I send this?")
Spring Boot → 200 OK + CORS headers  (answering: "yes, from localhost:5173")
Browser → POST /api/auth/login  (actual request)
Spring Boot → 200 OK + JWT
```

WHY? The browser wants to check permissions before sending sensitive data.
Spring Security must explicitly handle OPTIONS requests or they'll be blocked by auth.

In our SecurityConfig: `.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()`
This lets preflight OPTIONS requests through without requiring JWT.

---

## 4. How Our Axios Instance Works

```
src/api/axios.js  ←  ONE configured axios instance
     ↑
     └── baseURL: '/api'  (Vite proxy forwards to localhost:8080/api in dev)
     └── Request interceptor: reads token from localStorage → adds Authorization header
     └── Response interceptor:
             success → returns response.data  (unwraps the ApiResponse wrapper)
             401     → clears token → redirects to /login
             error   → extracts message from response.data.message
```

**The response unwrapping:**
Spring Boot always returns:
```json
{ "success": true, "message": "Login successful", "data": { "token": "eyJ..." } }
```
Our interceptor does `return response.data` — so every `await api.post(...)` returns
the ApiResponse object directly. In LoginPage: `res.data.token` gets the token.

---

## 5. Vite Proxy vs CORS

**In development** — we use Vite's dev proxy:
```js
// vite.config.js
proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
```
The browser talks to Vite (5173). Vite forwards /api/* to Spring Boot (8080).
From the browser's perspective, it's same-origin — NO CORS needed.

**But we still add CORS in Spring Boot** because:
1. Production: React is served from a real domain, not Vite proxy
2. Postman / mobile apps / other frontends need it
3. It's a production-readiness requirement

So in dev: proxy avoids CORS. In prod: CORS headers enable it.

---

## 6. useEffect — Fetching Data When a Component Loads

```jsx
useEffect(() => {
  // Side effect: runs AFTER the component renders
  fetchProducts()
}, [])  // ← empty array = run once on mount, never again
```

**Why useEffect, not just calling fetchProducts() directly?**
If you call an async function directly in the component body:
- It runs on every re-render (every state change)
- React doesn't know to wait for it
- It can cause infinite loops

useEffect separates "rendering" (pure) from "side effects" (async, subscriptions, timers).

**Dependency array rules:**
- `[]` — run once on mount
- `[id]` — run when `id` changes (e.g., navigate to different product)
- No array — run on EVERY render (almost never what you want)

---

## Interview Q&A — Phase F-2

**Q: What is CORS and who enforces it?**
A: Cross-Origin Resource Sharing. The BROWSER enforces it, not the server. The server
receives every request. The browser reads the `Access-Control-Allow-Origin` response
header and decides whether to let the JS code see the response. No header = browser
blocks it. This is why disabling CORS on the server "fixes" it — the server is now
explicitly telling the browser "this origin is allowed."

**Q: What is a preflight request?**
A: An HTTP OPTIONS request the browser sends before a "non-simple" cross-origin request
(POST with JSON, any request with custom headers like Authorization). The browser asks
"can I send this request with these headers?" The server responds with CORS headers.
If the preflight succeeds, the browser sends the real request. If it fails, the real
request is never sent. This is why you must permitAll() on OPTIONS in Spring Security.

**Q: Why use an Axios interceptor instead of adding the token in each request?**
A: DRY principle — Don't Repeat Yourself. Without interceptor: every API call needs
`headers: { Authorization: \`Bearer \${token}\` }`. That's 30+ places to update if
the header format changes, and easy to forget. With interceptor: one place, automatic,
consistent. Same reason you have Spring's OncePerRequestFilter — centralized cross-cutting concern.

**Q: What does useEffect's dependency array do?**
A: Controls WHEN the effect re-runs. Empty array `[]`: once on mount (like componentDidMount).
`[value]`: re-runs whenever `value` changes. No array: re-runs on every render.
React compares each dependency with `Object.is()` between renders — if any changed, effect fires.
