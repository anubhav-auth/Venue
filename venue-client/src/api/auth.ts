import api from './client'

interface LoginResponse {
  token: string
  role: string
  username: string
  name: string | null
  isTeamLead: boolean
  assignedRoomId: number | null
  assignments: { day: string; roomId: number; roomName: string }[] | null
}

export const login = (username: string, password: string) =>
  api.post<LoginResponse>('/auth/login', { username, password })
