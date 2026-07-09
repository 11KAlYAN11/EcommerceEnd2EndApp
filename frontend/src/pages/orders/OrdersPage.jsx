import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { ordersApi } from '../../api/orders'
import { useToast } from '../../context/ToastContext'
import { formatPrice, formatDate, statusBadgeClass } from '../../utils/format'
import Spinner from '../../components/common/Spinner'
import './Orders.css'

export default function OrdersPage() {
  const [orders,  setOrders]  = useState([])
  const [loading, setLoading] = useState(true)
  const [page,    setPage]    = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const toast = useToast()

  useEffect(() => {
    ordersApi.getMyOrders({ page, size: 10 })
      .then(res => {
        const pageData = res.data
        setOrders(pageData.content || [])
        setTotalPages(pageData.totalPages || 0)
      })
      .catch(() => toast.error('Failed to load orders'))
      .finally(() => setLoading(false))
  }, [page])

  if (loading) return <div style={{ padding: '2rem' }}><Spinner /></div>

  return (
    <div className="orders-page">
      <h1 className="page-title">My Orders</h1>

      {orders.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">📋</div>
          <h3>No orders yet</h3>
          <p>Your order history will appear here</p>
          <Link to="/" className="btn btn-primary" style={{ marginTop: '1rem' }}>Start Shopping</Link>
        </div>
      ) : (
        <>
          <div className="orders-list">
            {orders.map(order => (
              <Link key={order.orderId} to={`/orders/${order.orderId}`} className="order-card card">
                <div className="order-card-header">
                  <div>
                    <span className="order-id">Order #{order.orderId}</span>
                    <span className="order-date">{formatDate(order.placedAt || order.createdAt)}</span>
                  </div>
                  <span className={`badge ${statusBadgeClass(order.status)}`}>{order.status}</span>
                </div>
                <div className="order-card-body">
                  <span>{order.items?.length || '—'} item(s)</span>
                  <span className="order-total">{formatPrice(order.totalPrice || order.totalAmount)}</span>
                </div>
              </Link>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="pagination">
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>&larr; Prev</button>
              {[...Array(totalPages)].map((_, i) => (
                <button key={i} className={page === i ? 'active' : ''} onClick={() => setPage(i)}>{i + 1}</button>
              ))}
              <button disabled={page === totalPages - 1} onClick={() => setPage(p => p + 1)}>Next &rarr;</button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
