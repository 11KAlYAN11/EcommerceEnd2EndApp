import api from './axios'

export const cartApi = {
  getCart: ()                        => api.get('/cart'),
  addItem: (productId, quantity)     => api.post('/cart/items', { productId, quantity }),
  updateItem: (cartItemId, quantity) => api.patch(`/cart/items/${cartItemId}?quantity=${quantity}`),
  removeItem: (cartItemId)           => api.delete(`/cart/items/${cartItemId}`),
  clearCart: ()                      => api.delete('/cart'),
}
