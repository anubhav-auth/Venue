// src/pages/CheckIn.tsx
import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { checkIn } from '@/api/checkin'
import { useAuthStore } from '@/store/authStore'
import { Html5Qrcode } from 'html5-qrcode'
import { Camera, CameraOff } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '@/api/client'

interface Scan { name: string; seat: string; success: boolean; time: string }

export default function CheckIn() {
  const role = useAuthStore(s => s.role)
  const [day, setDay] = useState<'day1' | 'day2'>('day1')
  const [scanning, setScanning] = useState(false)
  const [result, setResult] = useState<{ type: 'success' | 'duplicate' | 'error'; msg: string } | null>(null)
  const [scans, setScans] = useState<Scan[]>([])
  const scannerRef = useRef<Html5Qrcode | null>(null)
  const processingRef = useRef(false)

  const { data: stats } = useQuery({
    queryKey: ['verifierDashboard', day],
    queryFn: () => api.get<{ roomName: string; capacity: number; checkedInCount: number; percentage: number }>(`verifier/dashboard/stats?day=${day}`).then(r => r.data),
    enabled: role === 'VERIFIER',
    refetchInterval: 10_000
  })

  const showResult = (type: 'success' | 'duplicate' | 'error', msg: string) => {
    setResult({ type, msg })
    setTimeout(() => setResult(null), 2500)
  }

  const startScan = async () => {
    setScanning(true)
    const scanner = new Html5Qrcode('qr-reader')
    scannerRef.current = scanner
    await scanner.start({ facingMode: 'environment' }, { fps: 10, qrbox: 280 },
      async (text) => {
        if (processingRef.current) return
        processingRef.current = true
        try {
          const r = await checkIn(text)
          const d = r.data
          showResult('success', `✅ ${d.studentName} — Seat ${d.seatNumber}`)
          setScans(p => [{ name: d.studentName, seat: d.seatNumber, success: true, time: new Date().toLocaleTimeString() }, ...p.slice(0, 9)])
        } catch (e: any) {
          const msg = e.response?.data?.message || e.response?.data?.error || 'Invalid QR'
          if (msg.toLowerCase().includes('already')) showResult('duplicate', `⚠️ Already checked in`)
          else showResult('error', `❌ ${msg}`)
        } finally {
          setTimeout(() => { processingRef.current = false }, 1500)
        }
      }, () => {})
  }

  const stopScan = async () => {
    setScanning(false)
    try { await scannerRef.current?.stop() } catch {}
  }

  const overlayColor = result?.type === 'success' ? 'bg-green-500' : result?.type === 'duplicate' ? 'bg-yellow-400' : 'bg-red-500'

  return (
    <div className="max-w-lg mx-auto space-y-6">
      <h1 className="text-xl font-semibold">Verifier Check-in</h1>

      {/* Day selector */}
      <div className="flex gap-2">
        {(['day1', 'day2'] as const).map(d => (
          <button key={d} onClick={() => setDay(d)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium ${day === d ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-600'}`}>
            {d === 'day1' ? 'Day 1' : 'Day 2'}
          </button>
        ))}
      </div>

      {/* Stats card */}
      {stats && (
        <div className="bg-white rounded-xl border p-5">
          <p className="text-sm text-gray-500">{stats.roomName}</p>
          <p className="text-3xl font-bold mt-1">{stats.checkedInCount} <span className="text-lg text-gray-400">/ {stats.capacity}</span></p>
          <div className="mt-2 h-2 bg-gray-100 rounded-full overflow-hidden">
            <div className="h-full bg-indigo-500 rounded-full transition-all" style={{ width: `${stats.percentage}%` }} />
          </div>
        </div>
      )}

      {/* Scanner */}
      <div className="bg-white rounded-xl border p-6 space-y-4 relative">
        <div id="qr-reader" className={scanning ? 'w-full rounded-lg overflow-hidden' : 'hidden'} />
        {result && (
          <div className={`absolute inset-0 ${overlayColor} rounded-xl flex items-center justify-center bg-opacity-90 z-10`}>
            <p className="text-white text-lg font-bold text-center px-4">{result.msg}</p>
          </div>
        )}
        <button onClick={scanning ? stopScan : startScan}
          className={`w-full flex items-center justify-center gap-2 py-3 rounded-lg font-medium text-sm ${scanning ? 'bg-red-100 text-red-600' : 'bg-indigo-600 text-white hover:bg-indigo-700'}`}>
          {scanning ? <><CameraOff size={16} /> Stop Scanner</> : <><Camera size={16} /> Open Scanner</>}
        </button>
      </div>

      {/* Recent scans */}
      {scans.length > 0 && (
        <div className="bg-white rounded-xl border p-5">
          <h2 className="text-sm font-semibold mb-3">Recent Scans</h2>
          <ul className="space-y-2">
            {scans.map((s, i) => (
              <li key={i} className="flex items-center justify-between text-sm">
                <span>{s.name} — <span className="text-gray-500">{s.seat}</span></span>
                <span className="text-xs text-gray-400">{s.time}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
