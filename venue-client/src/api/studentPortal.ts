import api from './client'

export interface StudentAssignmentDto {
  studentId: number
  name: string
  regNo: string
  role: string
  roomName: string | null
  building: string | null
  floor: string | null
  seatNumber: string | null
  day: string | null
  qrCodeData: string | null
}

export const getStudentAssignment = () =>
  api.get<StudentAssignmentDto>('/student/assignment')

export const getStudentProfile = () =>
  api.get<StudentAssignmentDto>('/student/profile')

export const downloadStudentQr = (day?: string) =>
  api.get('/student/qr', { params: day ? { day } : {}, responseType: 'blob' })
