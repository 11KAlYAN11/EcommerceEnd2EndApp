import { Routes, Route, Navigate } from 'react-router-dom'
import Navbar from './components/layout/Navbar'
import ProtectedRoute from './components/layout/ProtectedRoute'
import AdminRoute from './components/layout/AdminRoute'

// Pages — auth
import LoginPage    from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'

// Pages — shop
import ProductListPage   from './pages/products/ProductListPage'
import ProductDetailPage from './pages/products/ProductDetailPage'
import CartPage          from './pages/cart/CartPage'
import OrdersPage        from './pages/orders/OrdersPage'
import OrderDetailPage   from './pages/orders/OrderDetailPage'
import PaymentPage       from './pages/payments/PaymentPage'

// Pages — admin
import AdminDashboardPage from './pages/admin/AdminDashboardPage'
import AdminProductsPage  from './pages/admin/AdminProductsPage'
import AdminCategoriesPage from './pages/admin/AdminCategoriesPage'
import AdminOrdersPage    from './pages/admin/AdminOrdersPage'

/*
 * App.jsx — the ROOT component. Everything renders inside this.
 *
 * <Routes> — looks at the current URL, picks the matching <Route>, renders it.
 * <Route path="/" element={<X />}> — when URL is "/", render component X.
 *
 * Route types we use:
 *   Public    — anyone can visit (products, login, register)
 *   Protected — must be logged in (cart, orders, payments)
 *   Admin     — must be logged in AND have ROLE_ADMIN
 *
 * <Navigate to="/" replace /> — redirect. `replace` means no history entry
 * (back button won't re-navigate to the 404).
 */
export default function App() {
  return (
    <>
      <Navbar />

      <Routes>
        {/* Public */}
        <Route path="/"           element={<ProductListPage />} />
        <Route path="/products/:id" element={<ProductDetailPage />} />
        <Route path="/login"      element={<LoginPage />} />
        <Route path="/register"   element={<RegisterPage />} />

        {/* Protected — login required */}
        <Route element={<ProtectedRoute />}>
          <Route path="/cart"            element={<CartPage />} />
          <Route path="/orders"          element={<OrdersPage />} />
          <Route path="/orders/:id"      element={<OrderDetailPage />} />
          <Route path="/payment/:orderId" element={<PaymentPage />} />
        </Route>

        {/* Admin — ROLE_ADMIN required */}
        <Route element={<AdminRoute />}>
          <Route path="/admin"              element={<AdminDashboardPage />} />
          <Route path="/admin/products"     element={<AdminProductsPage />} />
          <Route path="/admin/categories"   element={<AdminCategoriesPage />} />
          <Route path="/admin/orders"       element={<AdminOrdersPage />} />
        </Route>

        {/* Catch-all */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  )
}
