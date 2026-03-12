import api from './client'

export interface Review {
  id: number
  studentId: number
  checkInId: number | null
  day: string
  reviewText: string
  addedByUsername: string
  addedAt: string
}

export const addReview = (
  checkInId: number,
  studentId: number,
  day: string,
  reviewText: string
) =>
  api.post<Review>(`/checkin/${checkInId}/review`, { studentId, day, reviewText })

export const getReviews = (studentId: number, day: string) =>
  api.get<Review[]>(`/admin/students/${studentId}/reviews`, { params: { day } })
