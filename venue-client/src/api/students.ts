import api from './client'

export interface Student {
  id: number; regNo: string; name: string;
  degree: string; passoutYear: number; role: string
}

export const getStudents = (page = 0, size = 50) =>
  api.get<{ content: Student[]; totalElements: number; totalPages: number }>(
    `/admin/students?page=${page}&size=${size}`
  )
export const createStudent = (data: Omit<Student, 'id'>) => api.post('/admin/students', data)
export const uploadCsv = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return api.post('/admin/students/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
export const deleteStudent = (id: number) => api.delete(`/admin/students/${id}`)
