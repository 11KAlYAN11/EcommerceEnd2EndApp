import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ordersApi } from '../../api/orders'
import { useToast } from '../../context/ToastContext'
import { formatPrice, formatDateTime, statusBadgeClass } from '../../utils/format'
import Spinner from '../../components/common/Spinner'
import './Orders.css'

export default function OrderDetailPage() {
  const { id } = useParams()
  const [order,   setOrder]   = useState(null)
  const [loading, setLoading] = useState(true)
  const toast    = useToast()
  const navigate = useNavigate()

  useEffect(() => {
    ordersApi.getById(id)
      .then(res => setOrder(res.data))
      .catch(() => toast.error('Order not found'))
      .finally(() => setLoading(false))
  }, [id])

  if (loading) return <div style={{ padding: '2rem' }}><Spinner /></div>
  if (!order)  return <div className="orders-page"><p>Order not found.</p></div>

  return (
    <div className="orders-page">
      <button onClick={() => navigate(-1)} className="btn btn-ghost btn-sm" style={{ marginBottom: '1rem' }}>
        &larr; Back
      </button>

      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1.5rem' }}>
        <h1 className="page-title" style={{ marginBottom: 0 }}>Order #{order.orderId}</h1>
        <span className={`badge ${statusBadgeClass(order.status)}`}>{order.status}</span>
      </div>

      <div className="order-detail-grid">
        {/* Items table */}
        <div className="card" style={{ padding: '1.25rem' }}>
          <h3 style={{ marginBottom: '1rem', fontSize: '1rem' }}>Items</h3>
          <table className="table">
            <thead>
              <tr><th>Product</th><th>Price</th><th>Qty</th><th>Subtotal</th></tr>
            </thead>
            <tbody>
              {(order.items || []).map(item => (
                <tr key={item.orderItemId || item.productId}>
                  <td>{item.productName}</td>
                  <td>{formatPrice(item.priceAtPurchase || item.price)}</td>
                  <td>{item.quantity}</td>
                  <td>{formatPrice(item.subtotal || (item.priceAtPurchase * item.quantity))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Summary */}
        <div className="card" style={{ padding: '1.25rem' }}>
          <h3 style={{ marginBottom: '1rem', fontSize: '1rem' }}>Summary</h3>
          <div className="order-meta">
            <div className="meta-row"><span>Order ID</span><span>#{order.orderId}</span></div>
            <div className="meta-row"><span>Placed on</span><span>{formatDateTime(order.placedAt || order.createdAt)}</span></div>
            <div className="meta-row"><span>Payment</span><span>{order.paymentMethod || 'COD'}</span></div>
            <div className="meta-row"><span>Status</span>
              <span className={`badge ${statusBadgeClass(order.status)}`}>{order.status}</span>
            </div>
            <div className="summary-divider" />
            <div className="meta-row summary-total">
              <span>Total</span>
              <span>{formatPrice(order.totalPrice || order.totalAmount)}</span>
            </div>
          </div>

          {order.shippingAddress && (
            <>
              <h3 style={{ marginTop: '1.25rem', marginBottom: '0.5rem', fontSize: '1rem' }}>Shipping</h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>{order.shippingAddress}</p>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
