import api from './axios'

/*
 * All auth-related API calls in one place.
 * Components call these functions — they never write axios.post() directly.
 * WHY: if the endpoint changes, change it here, not in 5 components.
 */
export const authApi = {
  register: (data) => api.post('/auth/register', data),
  // returns ApiResponse { data: { token } }

  login: (data) => api.post('/auth/login', data),
  // returns ApiResponse { data: { token } }
}
