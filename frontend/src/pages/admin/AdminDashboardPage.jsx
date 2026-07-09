import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { adminApi } from '../../api/admin'
import { useToast } from '../../context/ToastContext'
import { formatPrice } from '../../utils/format'
import Spinner from '../../components/common/Spinner'
import './Admin.css'

export default function AdminDashboardPage() {
  const [stats,   setStats]   = useState(null)
  const [loading, setLoading] = useState(true)
  const toast = useToast()

  useEffect(() => {
    adminApi.getDashboardStats()
      .then(res => setStats(res.data))
      .catch(() => toast.error('Failed to load dashboard'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <div style={{ padding: '2rem' }}><Spinner /></div>

  return (
    <div className="admin-page">
      <h1 className="page-title">Admin Dashboard</h1>

      <div className="stat-grid">
        <StatCard label="Total Revenue" value={formatPrice(stats?.totalRevenue || 0)} icon="💰" color="blue" />
        <StatCard label="Total Orders"  value={stats?.totalOrders  || 0} icon="📦" color="purple" />
        <StatCard label="Total Users"   value={stats?.totalUsers   || 0} icon="👥" color="green" />
        <StatCard label="Total Products" value={stats?.totalProducts || 0} icon="🛍️" color="orange" />
      </div>

      <div className="admin-links">
        <Link to="/admin/products"   className="admin-quick-link card">
          <span className="al-icon">🛍️</span>
          <div>
            <div className="al-title">Manage Products</div>
            <div className="al-sub">Add, edit, delete products</div>
          </div>
        </Link>
        <Link to="/admin/categories" className="admin-quick-link card">
          <span className="al-icon">📂</span>
          <div>
            <div className="al-title">Manage Categories</div>
            <div className="al-sub">Organise product categories</div>
          </div>
        </Link>
        <Link to="/admin/orders" className="admin-quick-link card">
          <span className="al-icon">📋</span>
          <div>
            <div className="al-title">Manage Orders</div>
            <div className="al-sub">View and update order status</div>
          </div>
        </Link>
      </div>
    </div>
  )
}

function StatCard({ label, value, icon, color }) {
  return (
    <div className={`stat-card stat-card-${color}`}>
      <div className="stat-icon">{icon}</div>
      <div className="stat-body">
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  )
}
