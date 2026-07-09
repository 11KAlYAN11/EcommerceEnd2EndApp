import { useState, useEffect } from 'react'
import { adminApi } from '../../api/admin'
import { useToast } from '../../context/ToastContext'
import { formatPrice, formatDate, statusBadgeClass } from '../../utils/format'
import Spinner from '../../components/common/Spinner'
import './Admin.css'

const STATUSES = ['', 'PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED']

export default function AdminOrdersPage() {
  const [orders,     setOrders]     = useState([])
  const [loading,    setLoading]    = useState(true)
  const [statusFilter, setStatusFilter] = useState('')
  const [page,       setPage]       = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [updating,   setUpdating]   = useState(null)
  const toast = useToast()

  function load() {
    adminApi.getOrders({ status: statusFilter || undefined, page, size: 10 })
      .then(res => {
        const pageData = res.data
        setOrders(pageData.content || [])
        setTotalPages(pageData.totalPages || 0)
      })
      .catch(() => toast.error('Failed to load orders'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [statusFilter, page])

  async function updateStatus(orderId, newStatus) {
    setUpdating(orderId)
    try {
      await adminApi.updateOrderStatus(orderId, newStatus)
      toast.success(`Order #${orderId} → ${newStatus}`)
      load()
    } catch (err) {
      toast.error(err.message)
    } finally {
      setUpdating(null)
    }
  }

  if (loading) return <div style={{ padding: '2rem' }}><Spinner /></div>

  return (
    <div className="admin-page">
      <div className="page-header">
        <h1 className="page-title">Orders</h1>
        <select
          className="form-input"
          style={{ width: 'auto' }}
          value={statusFilter}
          onChange={e => { setStatusFilter(e.target.value); setPage(0) }}
        >
          {STATUSES.map(s => <option key={s} value={s}>{s || 'All Statuses'}</option>)}
        </select>
      </div>

      <div className="card" style={{ overflow: 'auto' }}>
        <table className="table">
          <thead>
            <tr><th>#</th><th>Customer</th><th>Date</th><th>Total</th><th>Status</th><th>Update Status</th></tr>
          </thead>
          <tbody>
            {orders.map(o => (
              <tr key={o.orderId || o.id}>
                <td>#{o.orderId || o.id}</td>
                <td>{o.userEmail || '—'}</td>
                <td>{formatDate(o.placedAt || o.createdAt)}</td>
                <td>{formatPrice(o.totalPrice || o.totalAmount)}</td>
                <td><span className={`badge ${statusBadgeClass(o.status)}`}>{o.status}</span></td>
                <td>
                  <select
                    className="form-input"
                    style={{ width: 'auto', fontSize: '0.82rem', padding: '0.25rem 0.5rem' }}
                    value={o.status}
                    disabled={updating === (o.orderId || o.id)}
                    onChange={e => updateStatus(o.orderId || o.id, e.target.value)}
                  >
                    {STATUSES.filter(s => s).map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="pagination">
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>&larr;</button>
          {[...Array(totalPages)].map((_, i) => (
            <button key={i} className={page === i ? 'active' : ''} onClick={() => setPage(i)}>{i + 1}</button>
          ))}
          <button disabled={page === totalPages - 1} onClick={() => setPage(p => p + 1)}>&rarr;</button>
        </div>
      )}
    </div>
  )
}
