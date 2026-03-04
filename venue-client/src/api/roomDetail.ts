// src/api/roomDetail.ts
import api from './client'

export interface SeatData {
  seatNumber: string | null
  studentId: number
  name: string
  regNo: string
  degree: string
  passoutYear: number
  checkedIn: boolean
  checkInTime: string | null
  verifierUsername: string | null
}

export interface RoomDetailResponse {
  id: number
  roomName: string
  building: string
  floor: string
  capacity: number
  seatsPerRow: number
  day: string
  assignedVerifiers: string[]
  seats: SeatData[]
  overflow: SeatData[]
}

export const getRoomDetail = (id: number, day: string) =>
  api.get<RoomDetailResponse>(`admin/rooms/${id}/details`, { params: { day } })
