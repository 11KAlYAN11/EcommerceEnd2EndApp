# Phase F-1 — Authentication (Login & Register)

> Same principle as the backend JWT phase — learn the WHY before the code.

---

## 1. Controlled vs Uncontrolled Inputs

This is the most fundamental React concept for forms.

### Uncontrolled (plain HTML way):
```html
<input type="text" id="email" />
<!-- To read value: document.getElementById('email').value -->
```
React doesn't know what's in the input until you manually read it.

### Controlled (React way):
```jsx
const [email, setEmail] = useState('')

<input
  value={email}
  onChange={(e) => setEmail(e.target.value)}
/>
```
React state IS the source of truth. The input always shows `email`.
Every keystroke → `onChange` fires → `setEmail` updates state → React re-renders → input shows new value.

**Why controlled inputs?**
- You can validate on every keystroke
- You can disable the submit button when fields are empty
- You can reset the form by just calling `setEmail('')`
- React's state and the DOM are always in sync

**Interview Q**: "What's the difference between controlled and uncontrolled components?"
- Controlled: state drives the input value. Input is a "dumb" reflection of state.
- Uncontrolled: DOM drives itself, you use a `ref` to read it when needed.
- React recommends controlled for most cases.

---

## 2. Form Submission Pattern

```jsx
async function handleSubmit(e) {
  e.preventDefault()   // ← WHY? Without this, browser reloads the page (HTML default)
  setLoading(true)
  setError('')
  try {
    const res = await authApi.login({ email, password })
    login(res.data.token)      // save to context + localStorage
    navigate(from || '/')      // redirect
  } catch (err) {
    setError(err.message)      // show error to user
  } finally {
    setLoading(false)          // ALWAYS re-enable the button
  }
}
```

**`finally` block is critical**: if you only set `loading=false` in the `try`,
a network error leaves the button disabled forever. `finally` runs regardless.

---

## 3. useNavigate vs \<Link\>

```jsx
// <Link> — declarative, for static navigation
<Link to="/register">Create account</Link>

// useNavigate() — programmatic, for navigation after an action
const navigate = useNavigate()
navigate('/dashboard')        // go forward
navigate(-1)                  // go back (like browser back button)
navigate('/login', { replace: true })  // replace history entry (no "back" to this page)
```

**Replace vs push**:
- `navigate('/home')` → pushes to history → user can press Back to return
- `navigate('/home', { replace: true })` → replaces current entry → Back skips it
- Use `replace: true` after login/register so Back doesn't return to the auth page

---

## 4. Redirect-Back-After-Login Pattern

**Problem**: User tries to visit `/cart` while not logged in.
`ProtectedRoute` redirects them to `/login`.
After login, they land on `/` — they lost their original destination.

**Solution**: Pass the intended URL through navigation state.

```jsx
// In ProtectedRoute.jsx:
<Navigate to="/login" state={{ from: location }} replace />

// In LoginPage.jsx:
const location = useLocation()
const from = location.state?.from?.pathname || '/'
// After login:
navigate(from, { replace: true })
```

`useLocation()` gives us the current location object, which includes the `state`
passed by the previous navigation. `?.` is optional chaining — safe if state is null.

---

## 5. useState — Multiple Fields Pattern

Two ways to handle multiple form fields:

### Option A — Individual states (clearer for beginners):
```jsx
const [email,    setEmail]    = useState('')
const [password, setPassword] = useState('')
const [loading,  setLoading]  = useState(false)
const [error,    setError]    = useState('')
```

### Option B — Single form object (cleaner for many fields):
```jsx
const [form, setForm] = useState({ email: '', password: '' })

// Update one field:
setForm(prev => ({ ...prev, email: e.target.value }))
// spread operator copies all existing keys, then overrides `email`
```

We use Option A for Login (2 fields) and Option B for Register (5 fields).
This shows you both patterns — pick whichever fits the situation.

---

## Interview Q&A — Phase F-1

**Q: What happens if you forget `e.preventDefault()` on form submit?**
A: The browser performs its default form action — a GET/POST request to the current URL,
causing a full page reload. All React state is lost. The SPA breaks.

**Q: Why store JWT in localStorage vs a cookie?**
A: localStorage: simple, works for SPAs, accessible by JS. Vulnerable to XSS (if an
attacker injects JS, they can steal the token). Cookie with `httpOnly`: JS cannot read it
(XSS-safe), but requires server setup and CSRF protection. For a learning project,
localStorage is standard. Production apps with high security requirements use httpOnly cookies.

**Q: What is `useLocation()` and when do you use it?**
A: Hook from React Router that returns the current location object:
`{ pathname, search, hash, state, key }`. Use it to: read query params, read navigation
state, know the current URL inside a component that doesn't receive it as a prop.

**Q: What is optional chaining (`?.`) and why is it useful in React?**
A: `a?.b?.c` returns `undefined` instead of throwing TypeError if `a` or `b` is null/undefined.
Common in React because initial state is often null/undefined and data arrives asynchronously.
`location.state?.from?.pathname` — safe even if no state was passed.
