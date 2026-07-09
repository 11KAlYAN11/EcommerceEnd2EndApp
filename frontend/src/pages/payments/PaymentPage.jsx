import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { paymentsApi } from '../../api/payments'
import { useToast } from '../../context/ToastContext'
import { formatPrice } from '../../utils/format'
import './Payment.css'

const METHODS = [
  { id: 'COD',   label: 'Cash on Delivery', icon: '💵', desc: 'Pay when you receive' },
  { id: 'UPI',   label: 'UPI',              icon: '📱', desc: 'Google Pay, PhonePe, BHIM' },
  { id: 'CARD',  label: 'Credit / Debit Card', icon: '💳', desc: 'Visa, Mastercard, RuPay' },
  { id: 'NET',   label: 'Net Banking',      icon: '🏦', desc: 'All major banks' },
]

export default function PaymentPage() {
  const { state } = useLocation()
  const order     = state?.order
  const navigate  = useNavigate()
  const toast     = useToast()

  const [method,     setMethod]     = useState('COD')
  const [processing, setProcessing] = useState(false)
  const [success,    setSuccess]    = useState(false)

  if (!order) {
    return (
      <div className="payment-page">
        <p>No order found. <button className="btn btn-ghost" onClick={() => navigate('/')}>Go Home</button></p>
      </div>
    )
  }

  async function handlePay() {
    setProcessing(true)
    try {
      await paymentsApi.processPayment({ orderId: order.id, paymentMethod: method })
      setSuccess(true)
      toast.success('Payment successful!')
      setTimeout(() => navigate(`/orders/${order.id}`), 2000)
    } catch (err) {
      toast.error(err.message || 'Payment failed')
    } finally {
      setProcessing(false)
    }
  }

  if (success) {
    return (
      <div className="payment-page">
        <div className="payment-success">
          <div className="success-icon">✅</div>
          <h2>Payment Successful!</h2>
          <p>Redirecting to your order…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="payment-page">
      <h1 className="page-title">Complete Payment</h1>

      <div className="payment-grid">
        <div className="payment-methods card">
          <h3>Select Payment Method</h3>
          {METHODS.map(m => (
            <label key={m.id} className={`method-option ${method === m.id ? 'selected' : ''}`}>
              <input type="radio" name="method" value={m.id}
                checked={method === m.id}
                onChange={() => setMethod(m.id)}
              />
              <span className="method-icon">{m.icon}</span>
              <div>
                <div className="method-label">{m.label}</div>
                <div className="method-desc">{m.desc}</div>
              </div>
            </label>
          ))}
        </div>

        <div className="payment-summary card">
          <h3>Order Summary</h3>
          <div className="meta-row"><span>Order #</span><span>{order.id}</span></div>
          <div className="meta-row summary-total">
            <span>Total</span>
            <span>{formatPrice(order.totalAmount)}</span>
          </div>
          <button
            className="btn btn-primary btn-full"
            style={{ marginTop: '1.5rem' }}
            onClick={handlePay}
            disabled={processing}
          >
            {processing ? 'Processing...' : `Pay ${formatPrice(order.totalAmount)}`}
          </button>
          <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '0.75rem', textAlign: 'center' }}>
            Secure payment powered by ShopEase
          </p>
        </div>
      </div>
    </div>
  )
}
