import axios from 'axios'
import { useAuthStore } from '@/store/authStore'

const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api' })

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// src/api/client.ts
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      // Only logout if it's an auth failure, not a missing endpoint
      const url = err.config?.url ?? ''
      const isAuthEndpoint = url.includes('/auth/')
      if (!isAuthEndpoint) {
        useAuthStore.getState().logout()
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  }
)


export default api
