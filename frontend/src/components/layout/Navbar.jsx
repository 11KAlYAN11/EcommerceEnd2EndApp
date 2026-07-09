import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useCart } from '../../context/CartContext'
import './Navbar.css'

export default function Navbar() {
  const { user, isAdmin, logout } = useAuth()
  const { itemCount } = useCart()
  const navigate  = useNavigate()
  const location  = useLocation()

  const isActive = (path) =>
    location.pathname === path || location.pathname.startsWith(path + '/')

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <nav className="navbar">
      <div className="navbar-inner">
        <Link to="/" className="navbar-brand">🛍️ ShopEase</Link>

        <div className="navbar-links">
          <Link to="/" className={isActive('/') && location.pathname === '/' ? 'nav-active' : ''}>
            Products
          </Link>

          {user ? (
            <>
              <Link to="/cart" className={`cart-link ${isActive('/cart') ? 'nav-active' : ''}`}>
                Cart
                {itemCount > 0 && <span className="cart-badge">{itemCount}</span>}
              </Link>
              <Link to="/orders" className={isActive('/orders') ? 'nav-active' : ''}>Orders</Link>
              {isAdmin && (
                <Link to="/admin" className={`admin-link ${isActive('/admin') ? 'nav-active' : ''}`}>
                  ⚙ Admin
                </Link>
              )}
              <div className="nav-user">
                <span className="user-chip">
                  👤 {user.sub?.split('@')[0]}
                  {isAdmin && <span className="role-tag">ADMIN</span>}
                </span>
                <button onClick={handleLogout} className="btn btn-ghost btn-sm">Logout</button>
              </div>
            </>
          ) : (
            <>
              <Link to="/login"    className={isActive('/login')    ? 'nav-active' : ''}>Login</Link>
              <Link to="/register" className="btn btn-primary btn-sm">Register</Link>
            </>
          )}
        </div>
      </div>
    </nav>
  )
}
