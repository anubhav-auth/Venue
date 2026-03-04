import api from './client'

export interface Allocation {
  assignmentId: number; studentId: number; regNo: string
  name: string; degree: string
  passoutYear: number; role: string; roomId: number
  roomName: string; building: string; floor: string
  seatNumber: string | null; day: string; overflow: boolean
}

export const getAllocations = (params: {
  day?: string; roomId?: string; page?: number; size?: number
}) => api.get<{
  content: Allocation[]; totalElements: number
  totalPages: number; last: boolean
}>('/admin/allocations', { params })

export const triggerAllocation = (day: string) =>
  api.post(`/admin/allocate?day=${day}`)

export const clearAllocations = (day: string) =>
  api.delete(`/admin/allocations?day=${day}`)

export const downloadQr = (studentId: number, day: string) =>
  api.get(`/admin/qr/${studentId}?day=${day}`, { responseType: 'blob' })
