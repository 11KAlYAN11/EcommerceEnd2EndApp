import api from './axios'

export const adminApi = {
  // Dashboard
  getDashboardStats: ()       => api.get('/admin/dashboard'),

  // Products — same endpoint, guarded by @PreAuthorize("hasRole('ADMIN')") on service
  getProducts:    (params)    => api.get('/products', { params }),
  createProduct:  (data)      => api.post('/products', data),
  updateProduct:  (id, data)  => api.put(`/products/${id}`, data),
  deleteProduct:  (id)        => api.delete(`/products/${id}`),

  // Categories (admin)
  createCategory: (data)      => api.post('/categories', data),
  updateCategory: (id, data)  => api.put(`/categories/${id}`, data),
  deleteCategory: (id)        => api.delete(`/categories/${id}`),

  // Orders (admin)
  getOrders:          (params)         => api.get('/admin/orders', { params }),
  updateOrderStatus:  (id, status)     => api.patch(`/admin/orders/${id}/status`, { status }),
}
