import api from './client'

export const checkIn = (qrPayload: string) =>
  api.post<{ studentName: string; regNo: string; roomName: string; seatNumber: string }>(
    '/checkin', { token: qrPayload }
  )
