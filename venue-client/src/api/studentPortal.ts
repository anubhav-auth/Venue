import api from './client'

export interface StudentAssignmentDto {
  studentId: number
  name: string
  regNo: string
  role: string
  roomName: string | null
  building: string | null
  floor: string | null
  seatNumber: string | null   // null until check-in scan assigns one
  day: string | null
  qrCodeData: string | null
  checkedIn: boolean
  checkInTime: string | null  // ISO 8601 datetime string
}

export const getStudentAssignment = () =>
  api.get<StudentAssignmentDto>('/student/assignment')

export const getStudentProfile = () =>
  api.get<StudentAssignmentDto>('/student/profile')

export const downloadStudentQr = (day?: string) =>
  api.get('/student/qr', { params: day ? { day } : {}, responseType: 'blob' })
