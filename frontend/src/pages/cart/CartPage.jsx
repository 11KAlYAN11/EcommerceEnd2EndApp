import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { cartApi } from '../../api/cart'
import { ordersApi } from '../../api/orders'
import { useCart } from '../../context/CartContext'
import { useToast } from '../../context/ToastContext'
import { formatPrice } from '../../utils/format'
import Spinner from '../../components/common/Spinner'
import './Cart.css'

export default function CartPage() {
  const [cart,    setCart]    = useState(null)
  const [loading, setLoading] = useState(true)
  const [placing, setPlacing] = useState(false)

  const { refreshCart } = useCart()
  const toast    = useToast()
  const navigate = useNavigate()

  function loadCart() {
    return cartApi.getCart()
      .then(res => setCart(res.data))
      .catch(() => toast.error('Failed to load cart'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadCart() }, [])

  async function updateQty(itemId, qty) {
    if (qty < 1) return
    try {
      await cartApi.updateItem(itemId, qty)
      await loadCart()
      await refreshCart()
    } catch (err) {
      toast.error(err.message)
    }
  }

  async function removeItem(itemId) {
    try {
      await cartApi.removeItem(itemId)
      await loadCart()
      await refreshCart()
      toast.success('Item removed')
    } catch (err) {
      toast.error(err.message)
    }
  }

  async function clearCart() {
    try {
      await cartApi.clearCart()
      await loadCart()
      await refreshCart()
      toast.info('Cart cleared')
    } catch (err) {
      toast.error(err.message)
    }
  }

  async function placeOrder() {
    setPlacing(true)
    try {
      const res = await ordersApi.create({ shippingAddress: 'Default Address', paymentMethod: 'COD' })
      // Interceptor returns ApiResponse; .data is the actual OrderResponse
      const order = res.data ?? res
      await refreshCart()
      toast.success('Order placed successfully!')
      navigate(`/orders/${order.orderId}`)
    } catch (err) {
      toast.error(err.message || 'Failed to place order')
    } finally {
      setPlacing(false)
    }
  }

  if (loading) return <div style={{ padding: '2rem' }}><Spinner /></div>

  const items = cart?.items || []
  const total = cart?.totalPrice ?? items.reduce((sum, i) => sum + (i.unitPrice || i.price || 0) * i.quantity, 0)

  return (
    <div className="cart-page">
      <h1 className="page-title">Shopping Cart</h1>

      {items.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">🛒</div>
          <h3>Your cart is empty</h3>
          <p>Add some products to get started</p>
          <Link to="/" className="btn btn-primary" style={{ marginTop: '1rem' }}>Browse Products</Link>
        </div>
      ) : (
        <div className="cart-layout">
          <div className="cart-items">
            {items.map(item => {
              const itemId    = item.cartItemId || item.id
              const unitPrice = item.unitPrice  || item.price || 0
              return (
                <div key={itemId} className="cart-item">
                  <div className="cart-item-info">
                    <Link to={`/products/${item.productId}`} className="cart-item-name">{item.productName}</Link>
                    <p className="cart-item-price">{formatPrice(unitPrice)} each</p>
                  </div>
                  <div className="cart-item-controls">
                    <button className="qty-btn" onClick={() => updateQty(itemId, item.quantity - 1)} disabled={item.quantity <= 1}>&#8722;</button>
                    <span className="qty-num">{item.quantity}</span>
                    <button className="qty-btn" onClick={() => updateQty(itemId, item.quantity + 1)}>&#43;</button>
                    <span className="cart-item-subtotal">{formatPrice(item.subtotal || unitPrice * item.quantity)}</span>
                    <button className="btn-icon-remove" onClick={() => removeItem(itemId)} title="Remove">&#10005;</button>
                  </div>
                </div>
              )
            })}

            <button onClick={clearCart} className="btn btn-ghost btn-sm" style={{ marginTop: '0.5rem' }}>
              Clear Cart
            </button>
          </div>

          <div className="cart-summary card">
            <h3 className="summary-title">Order Summary</h3>
            <div className="summary-row">
              <span>Items ({items.length})</span>
              <span>{formatPrice(total)}</span>
            </div>
            <div className="summary-row">
              <span>Shipping</span>
              <span className="text-success">Free</span>
            </div>
            <div className="summary-divider" />
            <div className="summary-row summary-total">
              <span>Total</span>
              <span>{formatPrice(total)}</span>
            </div>
            <button
              className="btn btn-primary btn-full"
              style={{ marginTop: '1rem' }}
              onClick={placeOrder}
              disabled={placing}
            >
              {placing ? 'Placing Order...' : 'Place Order'}
            </button>
            <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '0.5rem', textAlign: 'center' }}>
              Payment collected on delivery
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
