import api from './client'

export interface Room {
  id: number; roomName: string; building: string
  floor: string; capacity: number; description?: string
}

export const getRooms = () => api.get<Room[]>('/admin/rooms')
export const createRoom = (data: Omit<Room, 'id'>) => api.post('/admin/rooms', data)
export const updateRoom = (id: number, data: Omit<Room, 'id'>) => api.put(`/admin/rooms/${id}`, data)
export const deleteRoom = (id: number) => api.delete(`/admin/rooms/${id}`)
