import api from '@/api/client'

export interface VolunteerDto {
  studentId: number          // ← was "id"
  regNo: string
  name: string
  email: string
  day1Attended: boolean
  day2Attended: boolean
  isPromoted: boolean
  canPromote: boolean
}

export interface UploadStatus {
  status: 'PROCESSING' | 'DONE' | 'ERROR'
  result?: { imported: number; skipped: number; errors: number; rowErrors: { row: number; reason: string }[] }
  message?: string
}

export interface VerifierDto {
  id: number
  username: string
  name: string
  assignments: { day: string; roomId: number; roomName: string }[]
}

export const getVolunteers = () =>
  api.get<VolunteerDto[]>('admin/volunteers')

export const adminScanVolunteer = (qrData: string) =>
  api.post<{ studentId: number; name: string; regNo: string }>('admin/volunteers/scan', { qrData })

export const promoteVolunteer = (id: number, assignments: { day: string; roomId: number }[]) =>
  api.post(`admin/volunteers/${id}/promote`, { assignments })

export const getVerifiers = () =>
  api.get<VerifierDto[]>('admin/verifiers')

export const demoteVerifier = (id: number) =>
  api.post(`admin/verifiers/${id}/demote`)

export const uploadVolunteerCsv = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return api.post<{ jobId: string }>('/admin/volunteers/upload', form)
}

export const uploadAudienceCsv = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return api.post<{ jobId: string }>('/admin/audience/upload', form)
}

export const pollVolunteerUploadStatus = (jobId: string) =>
  api.get<UploadStatus>(`/admin/volunteers/upload/status/${jobId}`)

export const pollAudienceUploadStatus = (jobId: string) =>
  api.get<UploadStatus>(`/admin/audience/upload/status/${jobId}`)
