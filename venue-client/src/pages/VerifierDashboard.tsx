import { useState, useEffect, useRef, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Html5Qrcode } from 'html5-qrcode';
import { getVerifierStats, scanQr } from '../api/verifier';
import { useAuthStore } from '../store/authStore';

// ── Env-driven config ────────────────────────────────────────────────────────
const QR_FPS     = Number(import.meta.env.VITE_QR_FPS)               || 10;
const QR_BOX_W   = Number(import.meta.env.VITE_QR_BOX_WIDTH)         || 240;
const QR_BOX_H   = Number(import.meta.env.VITE_QR_BOX_HEIGHT)        || 240;
const REFETCH_MS = Number(import.meta.env.VITE_STATS_REFETCH_INTERVAL) || 30_000;

interface RecentScan {
  id: string;
  studentName: string;
  seatNumber: string | null;
  result: 'success' | 'duplicate' | 'error';
  message: string;
  time: Date;
}

type ScanState = 'idle' | 'success' | 'duplicate' | 'error';

export default function VerifierDashboard() {
  const { logout, name: verifierName, assignments } = useAuthStore();
  const days = assignments?.map((a: any) => a.day) ?? ['day1'];
  const [selectedDay, setSelectedDay] = useState<string>(days[0]);
  const [scannerActive, setScannerActive] = useState(false);
  const [scanState, setScanState] = useState<ScanState>('idle');
  const [lastScan, setLastScan] = useState<RecentScan | null>(null);
  const [recentScans, setRecentScans] = useState<RecentScan[]>([]);
  const processingRef = useRef(false);
  const scannerRef = useRef<Html5Qrcode | null>(null);

  const { data: stats, refetch: refetchStats } = useQuery({
    queryKey: ['verifierStats', selectedDay],
    queryFn: () => getVerifierStats(selectedDay).then(r => r.data),
    refetchInterval: REFETCH_MS,                                      // ← was 30_000
    enabled: !!selectedDay,
  });

  const handleScanSuccess = useCallback(async (qrData: string) => {
    if (processingRef.current) return;
    processingRef.current = true;

    try {
      const { data } = await scanQr(qrData, selectedDay);
      const scan: RecentScan = {
        id: Date.now().toString(),
        studentName: data.studentName ?? 'Unknown',
        seatNumber: data.seatNumber ?? null,
        result: data.success ? 'success' : 'duplicate',
        message: data.message,
        time: new Date(),
      };
      setScanState(data.success ? 'success' : 'duplicate');
      setLastScan(scan);
      setRecentScans(prev => [scan, ...prev].slice(0, 10));
      if (data.success) refetchStats();
    } catch (err: any) {
      const scan: RecentScan = {
        id: Date.now().toString(),
        studentName: '—',
        seatNumber: null,
        result: 'error',
        message: err?.response?.data?.message ?? 'Invalid QR',
        time: new Date(),
      };
      setScanState('error');
      setLastScan(scan);
      setRecentScans(prev => [scan, ...prev].slice(0, 10));
    } finally {
      setTimeout(() => { setScanState('idle'); processingRef.current = false; }, 2200);
    }
  }, [selectedDay, refetchStats]);

  // Start / stop scanner
  useEffect(() => {
    if (!scannerActive) return;
    const scanner = new Html5Qrcode('qr-reader');
    scannerRef.current = scanner;
    scanner.start(
      { facingMode: 'environment' },
      { fps: QR_FPS, qrbox: { width: QR_BOX_W, height: QR_BOX_H } }, // ← was hardcoded
      handleScanSuccess,
      () => {}
    ).catch(() => { setScannerActive(false); });
    return () => { scanner.stop().catch(() => {}); };
  }, [scannerActive, handleScanSuccess]);

  const overlayBg: Record<ScanState, string> = {
    idle: '',
    success: 'bg-green-500',
    duplicate: 'bg-yellow-400',
    error: 'bg-red-500',
  };

  const statusStyle: Record<string, string> = {
    high: 'text-green-700 bg-green-50',
    medium: 'text-yellow-700 bg-yellow-50',
    low: 'text-red-700 bg-red-50',
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b px-4 py-3 flex items-center justify-between sticky top-0 z-10">
        <div>
          <h1 className="font-semibold text-gray-800">Verifier Portal</h1>
          <p className="text-xs text-gray-400">{verifierName}</p>
        </div>
        <button onClick={logout} className="text-sm text-red-500 hover:text-red-700 font-medium">
          Logout
        </button>
      </div>

      <div className="max-w-md mx-auto px-4 py-4 space-y-4">

        {/* Day Selector — shown only if assigned to multiple days */}
        {days.length > 1 && (
          <div className="flex gap-2">
            {days.map((day: string) => (
              <button
                key={day}
                onClick={() => setSelectedDay(day)}
                className={`flex-1 py-2 rounded-lg text-sm font-medium transition-colors ${
                  selectedDay === day
                    ? 'bg-indigo-600 text-white shadow-sm'
                    : 'bg-white border border-gray-200 text-gray-600 hover:border-indigo-300'
                }`}
              >
                {day === 'day1' ? 'Day 1' : 'Day 2'}
              </button>
            ))}
          </div>
        )}

        {/* Stats Card */}
        {stats && (
          <div className="bg-white rounded-xl border border-gray-100 p-4 space-y-3 shadow-sm">
            <div>
              <p className="text-lg font-semibold text-gray-800">{stats.roomName}</p>
              <p className="text-sm text-gray-400">{stats.building} · Floor {stats.floor}</p>
            </div>
            <div className="grid grid-cols-3 gap-2 text-center">
              {[
                { label: 'Checked In', value: stats.checkedInCount, color: 'text-green-600' },
                { label: 'Remaining',  value: stats.remaining,      color: 'text-orange-500' },
                { label: 'Capacity',   value: stats.capacity,       color: 'text-gray-700' },
              ].map(({ label, value, color }) => (
                <div key={label} className="bg-gray-50 rounded-lg py-2">
                  <p className={`text-2xl font-bold ${color}`}>{value}</p>
                  <p className="text-xs text-gray-400 mt-0.5">{label}</p>
                </div>
              ))}
            </div>
            <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${
                  stats.status === 'high' ? 'bg-green-500' :
                  stats.status === 'medium' ? 'bg-yellow-400' : 'bg-red-500'
                }`}
                style={{ width: `${Math.min(stats.percentage, 100)}%` }}
              />
            </div>
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-400">{stats.percentage.toFixed(1)}% filled</span>
              <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${statusStyle[stats.status]}`}>
                {stats.status.toUpperCase()}
              </span>
            </div>
          </div>
        )}

        {/* QR Scanner */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          {!scannerActive ? (
            <button
              onClick={() => setScannerActive(true)}
              className="w-full py-10 flex flex-col items-center gap-3 text-indigo-600 hover:bg-indigo-50 transition-colors"
            >
              <div className="w-16 h-16 rounded-full bg-indigo-100 flex items-center justify-center">
                <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                    d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
                </svg>
              </div>
              <span className="font-medium text-lg">Open Scanner</span>
              <span className="text-sm text-gray-400">Tap to activate camera</span>
            </button>
          ) : (
            <div className="relative">
              {/* Feedback Overlay */}
              {scanState !== 'idle' && (
                <div className={`absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 ${overlayBg[scanState]} bg-opacity-90 transition-all`}>
                  <p className="text-3xl">{
                    scanState === 'success' ? '✓' : scanState === 'duplicate' ? '⚠' : '✗'
                  }</p>
                  <p className="text-white font-bold text-xl">{
                    scanState === 'success' ? 'Checked In!' :
                    scanState === 'duplicate' ? 'Already Scanned' : 'Invalid QR'
                  }</p>
                  {lastScan?.studentName !== '—' && (
                    <p className="text-white text-sm">{lastScan?.studentName}</p>
                  )}
                  {lastScan?.seatNumber && (
                    <p className="text-white text-sm opacity-80">Seat {lastScan.seatNumber}</p>
                  )}
                </div>
              )}
              <div id="qr-reader" className="w-full" />
              <button
                onClick={() => { setScannerActive(false); scannerRef.current?.stop().catch(() => {}); }}
                className="absolute top-2 right-2 z-10 bg-white rounded-full p-1.5 shadow-md"
              >
                <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          )}
        </div>

        {/* Recent Scans */}
        {recentScans.length > 0 && (
          <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
            <p className="px-4 py-3 text-sm font-semibold text-gray-600 border-b">Recent Scans</p>
            <div className="divide-y">
              {recentScans.map(scan => (
                <div key={scan.id} className="px-4 py-3 flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-gray-800">{scan.studentName}</p>
                    <p className="text-xs text-gray-400">
                      {scan.seatNumber ? `Seat ${scan.seatNumber} · ` : ''}
                      {scan.time.toLocaleTimeString()}
                    </p>
                  </div>
                  <span className={`w-3 h-3 rounded-full ${
                    scan.result === 'success' ? 'bg-green-500' :
                    scan.result === 'duplicate' ? 'bg-yellow-400' : 'bg-red-500'
                  }`} />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
