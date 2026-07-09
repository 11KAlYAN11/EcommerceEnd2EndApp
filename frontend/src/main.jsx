import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider }  from './context/AuthContext'
import { CartProvider }  from './context/CartContext'
import { ToastProvider } from './context/ToastContext'
import './index.css'

/*
 * Provider nesting order matters — each layer makes its context available to everything inside it:
 *   BrowserRouter → routing hooks (useNavigate, useLocation) available everywhere
 *   AuthProvider  → useAuth() available everywhere
 *   CartProvider  → useCart() available (depends on useAuth internally)
 *   ToastProvider → useToast() available everywhere + renders the toast container
 *   App           → the actual UI tree
 */
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <CartProvider>
          <ToastProvider>
            <App />
          </ToastProvider>
        </CartProvider>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>
)
