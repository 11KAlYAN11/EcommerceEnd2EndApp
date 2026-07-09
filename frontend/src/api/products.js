import api from './axios'

export const productsApi = {
  getAll: (params) => api.get('/products', { params }),
  // params: { page, size, sortBy, search }

  getById: (id) => api.get(`/products/${id}`),

  getByCategory: (categoryId, params) =>
    api.get(`/products/category/${categoryId}`, { params }),

  search: (params) => api.get('/search/products', { params }),
  // params: { q, minPrice, maxPrice, categoryId, page, size, sort }

  create: (data) => api.post('/products', data),
  update: (id, data) => api.put(`/products/${id}`, data),
  delete: (id) => api.delete(`/products/${id}`),
}
