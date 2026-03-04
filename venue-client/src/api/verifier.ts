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

export interface ScanResult {
  success: boolean;
  message: string;
  studentName?: string;
  seatNumber?: string;
  roomName?: string;
}

export const getVerifierStats = (day: string) =>
  api.get<VerifierStats>('/verifier/dashboard/stats', { params: { day } });

export const scanQr = (qrData: string, day: string) =>
  api.post<ScanResult>('/verifier/check-in/scan', { qrData, day });
