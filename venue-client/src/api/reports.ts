// src/api/reports.ts
import api from './client'

// Adjust these URLs if your ReportController.java uses different mapping paths
export const downloadFullAttendance = () => 
  api.get('/reports/attendance', { responseType: 'blob' })

export const downloadRoomSummary = () => 
  api.get('/reports/room-summary', { responseType: 'blob' })

export const downloadNotCheckedIn = () => 
  api.get('/reports/not-checked-in', { responseType: 'blob' })

export const downloadVerifierActivity = () => 
  api.get('/reports/verifier-activity', { responseType: 'blob' })