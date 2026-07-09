// Pure helper functions — no React, no side effects, easy to unit test

// Generic product placeholder — shown when an image URL fails to load (404, ORB block, etc.)
export const PRODUCT_PLACEHOLDER =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='400' height='400' viewBox='0 0 400 400'%3E%3Crect width='400' height='400' fill='%231e293b'/%3E%3Crect x='120' y='100' width='160' height='140' rx='12' fill='%23334155'/%3E%3Ccircle cx='200' cy='165' r='30' fill='%2364748b'/%3E%3Cpath d='M140 230 Q170 190 200 210 Q230 230 260 200 L260 240 Q200 260 140 240Z' fill='%2364748b'/%3E%3Ctext x='200' y='295' text-anchor='middle' font-family='sans-serif' font-size='14' fill='%2394a3b8'%3ENo Image%3C/text%3E%3C/svg%3E"

export const imgFallback = (e) => { e.target.onerror = null; e.target.src = PRODUCT_PLACEHOLDER }

export const formatPrice = (amount) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount)

export const formatDate = (iso) =>
  new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })

export const formatDateTime = (iso) =>
  new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })

// Returns only the modifier class — use as: className={`badge ${statusBadgeClass(status)}`}
export const statusBadgeClass = (status) => {
  const map = {
    PENDING:    'badge-yellow',
    CONFIRMED:  'badge-blue',
    PROCESSING: 'badge-blue',
    SHIPPED:    'badge-blue',
    DELIVERED:  'badge-green',
    CANCELLED:  'badge-red',
    COMPLETED:  'badge-green',
    FAILED:     'badge-red',
  }
  return map[status] || 'badge-gray'
}
