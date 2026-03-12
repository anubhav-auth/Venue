import api from './client'

export const checkIn = (qrPayload: string) =>
  api.post<{
    success: boolean
    alreadyCheckedIn?: boolean
    studentId?: number
    name?: string
    regNo?: string
    degree?: string
    seatNumber?: string
    roomName?: string
    verifierUsername?: string
    checkInTime?: string
    message?: string
  }>('/checkin', { token: qrPayload })
