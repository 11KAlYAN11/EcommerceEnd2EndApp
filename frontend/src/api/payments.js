import api from './axios'

export const paymentsApi = {
  processPayment: (data)     => api.post('/payments/process', data),
  initiate:  (orderId, method = 'UPI') => api.post(`/payments/initiate/${orderId}?method=${method}`),
  confirm:   (orderId) => api.post(`/payments/confirm/${orderId}`),
  fail:      (orderId) => api.post(`/payments/fail/${orderId}`),
  getForOrder:(orderId) => api.get(`/payments/order/${orderId}`),
}
