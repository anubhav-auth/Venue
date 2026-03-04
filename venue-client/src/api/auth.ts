import api from './client'

export const login = (username: string, password: string) =>
  api.post<{ token: string; role: string; username: string }>('/auth/login', { username, password })
