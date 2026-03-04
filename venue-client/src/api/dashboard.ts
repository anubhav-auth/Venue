// src/api/dashboard.ts
import api from './client'

export interface RoomStats {
  roomId: number
  roomName: string
  capacity: number
  checkedIn: number
  percentage: number
  status: 'high' | 'medium' | 'low'
}

export interface DashboardStats {
  totalStudents: number
  checkedIn: number
  percentage: number
  notCheckedIn: number
  rooms: RoomStats[]
  lastUpdated: string
}

export const getDashboardStats = (day: string) =>
  api.get<DashboardStats>('admin/dashboard/stats', { params: { day } })

export const closeDay = (day: string) =>
  api.post<{ dayClosed: string; autodemotedCount: number; autodemoted: string[] }>(
    `admin/days/${day}/close`
  )

export const getPreCloseSummary = (day: string) =>
  api.get<{ verifiersTodemote: string[] }>(`admin/days/${day}/pre-close-summary`)
