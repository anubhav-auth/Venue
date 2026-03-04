// src/api/volunteers.ts
import api from './client'

export interface VolunteerDto {
  id: number
  regNo: string
  name: string
  day1Attended: boolean
  day2Attended: boolean
  isPromoted: boolean
  canPromote: boolean
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
  return api.post<{ imported: number; skipped: number; errors: { row: number; reason: string }[] }>(
    'admin/volunteers/upload', form
  )
}

export const uploadAudienceCsv = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return api.post<{ imported: number; skipped: number; errors: { row: number; reason: string }[] }>(
    'admin/audience/upload', form
  )
}
