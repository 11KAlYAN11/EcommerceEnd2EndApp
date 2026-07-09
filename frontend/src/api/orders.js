import api from './axios'

export const ordersApi = {
  create:      (data)    => api.post('/orders', data),
  getMyOrders: (params)  => api.get('/orders', { params }),
  getById:     (id)      => api.get(`/orders/${id}`),
  cancel:      (id)      => api.post(`/orders/${id}/cancel`),
}
