import api from './client'

export interface RosterStatus {
  status: 'PROCESSING' | 'DONE' | 'ERROR' | 'NOT_FOUND'
  result?: { imported: number; skipped: number; errors: number }
  message?: string
}

export interface RosterEntry {
  id: number
  studentId: number
  regNo: string
  name: string
  degree: string | null
  day: string
  uploadedAt: string
}

export const uploadRoster = (roomId: number, day: string, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return api.post<{ jobId: string }>(`/admin/rooms/${roomId}/roster?day=${day}`, form)
}

export const pollRosterStatus = (roomId: number, jobId: string) =>
  api.get<RosterStatus>(`/admin/rooms/${roomId}/roster/status/${jobId}`)

export const getRoster = (roomId: number, day: string, page = 0, size = 50) =>
  api.get<{
    content: RosterEntry[]
    totalElements: number
    totalPages: number
    number: number
  }>(`/admin/rooms/${roomId}/roster`, { params: { day, page, size } })

export const clearRoster = (roomId: number, day: string) =>
  api.delete(`/admin/rooms/${roomId}/roster`, { params: { day } })
