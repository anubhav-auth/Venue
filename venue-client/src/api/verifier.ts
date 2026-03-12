import api from './client';

export interface VerifierStats {
  verifierName: string;
  roomId: number;
  roomName: string;
  building: string;
  floor: string;
  day: string;
  capacity: number;
  checkedInCount: number;
  remaining: number;
  percentage: number;
  status: 'high' | 'medium' | 'low';
}

export interface StudentScanResult {
  success: boolean;
  alreadyCheckedIn?: boolean;
  studentId?: number;
  name?: string;
  regNo?: string;
  degree?: string;
  branch?: string;
  passoutYear?: number;
  seatNumber?: string;
  roomName?: string;
  verifierUsername?: string;
  checkInTime?: string;
  message?: string;
  checkInId?: number | null;
}

export const getVerifierStats = (day: string) =>
  api.get<VerifierStats>('/verifier/dashboard/stats', { params: { day } });

export const scanQr = (qrData: string, day: string) =>
  api.post<StudentScanResult>('/verifier/check-in/scan', { qrData, day });

export const updateVerifier = (
  id: number,
  data: { isTeamLead: boolean; assignedRoomId: number | null }
) => api.patch(`/admin/verifiers/${id}`, data);

export const getMyRoom = () =>
  api.get('/verifier/my-room');

export const markVolunteerAbsent = (volunteerId: number) =>
  api.post(`/admin/volunteers/${volunteerId}/mark-absent`);
