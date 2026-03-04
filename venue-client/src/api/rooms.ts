// src/api/rooms.ts
import api from './client'

export interface Room {
  id: number
  roomName: string
  building: string
  floor: string
  capacity: number
  seatsPerRow: number
  day: 'day1' | 'day2'
  createdAt?: string
}

export const getRooms = (day?: string) =>
  api.get<Room[]>('admin/rooms', { params: day ? { day } : {} })

export const createRoom = (data: Omit<Room, 'id' | 'createdAt'>) =>
  api.post<Room>('admin/rooms', data)

export const updateRoom = (id: number, data: Omit<Room, 'id' | 'createdAt'>) =>
  api.put<Room>(`admin/rooms/${id}`, data)

export const deleteRoom = (id: number) =>
  api.delete(`admin/rooms/${id}`)

export const uploadRoomCsv = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return api.post<{ imported: number; skipped: number; errors: { row: number; reason: string }[] }>(
    'admin/rooms/upload', form
  )
}
