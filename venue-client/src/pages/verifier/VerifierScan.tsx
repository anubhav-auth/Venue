import { useState, useEffect, useRef, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Html5Qrcode } from 'html5-qrcode'
import { getVerifierStats, scanQr, type StudentScanResult } from '@/api/verifier'
import { addReview } from '@/api/reviews'
import { useAuthStore } from '@/store/authStore'
import toast from 'react-hot-toast'

const QR_FPS = Number(import.meta.env.VITE_QR_FPS) || 10
const QR_BOX_W = Number(import.meta.env.VITE_QR_BOX_WIDTH) || 240
const QR_BOX_H = Number(import.meta.env.VITE_QR_BOX_HEIGHT) || 240
const REFETCH_MS = Number(import.meta.env.VITE_STATS_REFETCH_INTERVAL) || 30_000

type ScanState = 'idle' | 'success' | 'duplicate' | 'error'

interface RecentScan {
  id: string
  name: string
  seatNumber: string | null
  result: ScanState
  time: Date
}

// ── Layout states ─────────────────────────────────────────────────────────────
// 'closed'  → "Open Scanner" button shown
// 'scanning'→ camera active
// 'result'  → camera stopped, result card shown (success or duplicate)
type ViewState = 'closed' | 'scanning' | 'result'

export default function VerifierScan() {
  const { assignments } = useAuthStore()
  const days = assignments?.map((a) => a.day) ?? ['day1']
  const [selectedDay, setSelectedDay] = useState<string>(days[0])

  const [view, setView] = useState<ViewState>('closed')
  const [scanState, setScanState] = useState<ScanState>('idle')
  const [lastResult, setLastResult] = useState<StudentScanResult | null>(null)
  const [recentScans, setRecentScans] = useState<RecentScan[]>([])

  // Review state — auto-open on duplicate
  const [showReview, setShowReview] = useState(false)
  const [reviewText, setReviewText] = useState('')
  const [reviewSubmitting, setReviewSubmitting] = useState(false)

  const processingRef = useRef(false)
  const scannerRef = useRef<Html5Qrcode | null>(null)

  const { data: stats, refetch: refetchStats } = useQuery({
    queryKey: ['verifierStats', selectedDay],
    queryFn: () => getVerifierStats(selectedDay).then((r) => r.data),
    refetchInterval: REFETCH_MS,
    enabled: !!selectedDay,
  })

  // ── Scan Again ───────────────────────────────────────────────────────────────
  const handleScanAgain = () => {
    setScanState('idle')
    setLastResult(null)
    setShowReview(false)
    setReviewText('')
    processingRef.current = false
    setView('scanning') // triggers camera useEffect
  }

  // ── Close scanner ────────────────────────────────────────────────────────────
  const handleClose = () => {
    scannerRef.current?.stop().catch(() => { })
    setView('closed')
    setScanState('idle')
    setLastResult(null)
    processingRef.current = false
  }

  // ── Review submit ────────────────────────────────────────────────────────────
  const handleSubmitReview = async () => {
    const checkInId = lastResult?.checkInId
    if (!checkInId || !reviewText.trim()) return
    setReviewSubmitting(true)
    try {
      await addReview(checkInId, lastResult?.studentId ?? 0, selectedDay, reviewText.trim())
      toast.success('Review added')
      setReviewText('')
      setShowReview(false)
    } catch {
      toast.error('Failed to add review')
    } finally {
      setReviewSubmitting(false)
    }
  }

  // ── QR scan handler ──────────────────────────────────────────────────────────
  const handleScanSuccess = useCallback(
    async (qrData: string) => {
      if (processingRef.current) return
      processingRef.current = true

      try {
        const { data } = await scanQr(qrData, selectedDay)
        const state: ScanState =
          data.success && !data.alreadyCheckedIn ? 'success'
            : data.success && data.alreadyCheckedIn ? 'duplicate'
              : 'error'

        if (state === 'error') {
          // Flash error on camera, auto-dismiss, keep scanning
          setScanState('error')
          setLastResult(data)
          setRecentScans((prev) => [{
            id: Date.now().toString(),
            name: data.name ?? 'Unknown',
            seatNumber: data.seatNumber || null,
            result: 'error' as ScanState,   // ← add this
            time: new Date(),
          }, ...prev].slice(0, 10))
          setTimeout(() => {
            setScanState('idle')
            setLastResult(null)
            processingRef.current = false
          }, 1500)
          return
        }

        // Success or duplicate — stop camera, show result card
        await scannerRef.current?.stop()
        setLastResult(data)
        setScanState(state)
        setShowReview(state === 'duplicate') // auto-open review for rescans
        setRecentScans((prev) => [{
          id: Date.now().toString(),
          name: data.name ?? 'Unknown',
          seatNumber: data.seatNumber || null,
          result: state,
          time: new Date(),
        }, ...prev].slice(0, 10))
        setView('result')
        if (state === 'success') refetchStats()

      } catch (err: any) {
        setScanState('error')
        setTimeout(() => {
          setScanState('idle')
          processingRef.current = false
        }, 1500)
      }
    },
    [selectedDay, refetchStats]
  )

  const handleScanSuccessRef = useRef(handleScanSuccess)
  useEffect(() => {
    handleScanSuccessRef.current = handleScanSuccess
  }, [handleScanSuccess])

  // ── Camera lifecycle ─────────────────────────────────────────────────────────
  useEffect(() => {
    if (view !== 'scanning') return
    const scanner = new Html5Qrcode('qr-reader-scan')
    scannerRef.current = scanner
    scanner
      .start(
        { facingMode: 'environment' },
        { fps: QR_FPS, qrbox: { width: QR_BOX_W, height: QR_BOX_H } },
        (qrData) => handleScanSuccessRef.current(qrData),  // ✅ stable wrapper
        () => { }
      )
      .catch(() => setView('closed'))
    return () => { scanner.stop().catch(() => { }) }
  }, [view])

  // ── Result card colors ───────────────────────────────────────────────────────
  const resultStyle: Record<'success' | 'duplicate', { bg: string; label: string; icon: string }> = {
    success: { bg: 'bg-green-500', label: 'Checked In!', icon: '✓' },
    duplicate: { bg: 'bg-yellow-400', label: 'Already Scanned', icon: '⚠' },
  }

  return (
    <div className="space-y-4">

      {/* Day selector */}
      {days.length > 1 && (
        <div className="flex gap-2">
          {days.map((d) => (
            <button key={d} onClick={() => setSelectedDay(d)}
              className={`flex-1 py-2 rounded-lg text-sm font-medium transition-colors ${selectedDay === d
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'bg-white border border-gray-200 text-gray-600 hover:border-indigo-300'
                }`}>
              {d === 'day1' ? 'Day 1' : 'Day 2'}
            </button>
          ))}
        </div>
      )}

      {/* Stats card */}
      {stats && (
        <div className="bg-white rounded-xl border border-gray-100 p-4 space-y-3 shadow-sm">
          <div>
            <p className="text-lg font-semibold text-gray-800">{stats.roomName}</p>
            <p className="text-sm text-gray-400">{stats.building} · Floor {stats.floor}</p>
          </div>
          <div className="grid grid-cols-3 gap-2 text-center">
            {[
              { label: 'Checked In', value: stats.checkedInCount, color: 'text-green-600' },
              { label: 'Remaining', value: stats.remaining, color: 'text-orange-500' },
              { label: 'Capacity', value: stats.capacity, color: 'text-gray-700' },
            ].map(({ label, value, color }) => (
              <div key={label} className="bg-gray-50 rounded-lg py-2">
                <p className={`text-2xl font-bold ${color}`}>{value}</p>
                <p className="text-xs text-gray-400 mt-0.5">{label}</p>
              </div>
            ))}
          </div>
          <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
            <div className={`h-full rounded-full transition-all duration-500 ${stats.status === 'high' ? 'bg-green-500'
              : stats.status === 'medium' ? 'bg-yellow-400' : 'bg-red-500'
              }`} style={{ width: `${Math.min(stats.percentage, 100)}%` }} />
          </div>
        </div>
      )}

      {/* ── RESULT CARD (replaces scanner entirely after scan) ── */}
      {view === 'result' && lastResult && (scanState === 'success' || scanState === 'duplicate') && (() => {
        const { bg, label, icon } = resultStyle[scanState]
        return (
          <div className={`${bg} rounded-2xl p-5 text-white shadow-lg space-y-4`}>
            {/* Header */}
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-full bg-white/20 flex items-center justify-center text-2xl font-bold">
                {icon}
              </div>
              <div>
                <p className="text-xl font-bold">{label}</p>
                <p className="text-sm opacity-80">{scanState === 'duplicate' ? 'This student was already checked in' : 'Successfully recorded'}</p>
              </div>
            </div>

            {/* Student details */}
            <div className="bg-white/10 rounded-xl p-4 space-y-1">
              <p className="text-lg font-bold">{lastResult.name}</p>
              <p className="text-sm opacity-90 font-mono">{lastResult.regNo}</p>
              {lastResult.degree && <p className="text-sm opacity-80">{lastResult.degree}</p>}
              {lastResult.seatNumber && (
                <p className="mt-2 text-xl font-black tracking-wide">
                  🪑 Seat {lastResult.seatNumber}
                </p>
              )}
              {lastResult.roomName && lastResult.roomName !== 'N/A' && (
                <p className="text-sm opacity-80">📍 {lastResult.roomName}</p>
              )}
            </div>

            {/* Review section */}
            <div className="space-y-2">
              <button
                onClick={() => setShowReview(v => !v)}
                className="w-full py-2.5 text-sm font-medium border border-white/40 rounded-xl hover:bg-white/10 transition-colors"
              >
                {showReview ? '✕ Cancel Review' : '📝 Add Review / Note'}
              </button>

              {showReview && (
                <div className="space-y-2">
                  <textarea
                    value={reviewText}
                    onChange={e => setReviewText(e.target.value)}
                    placeholder="Write a note about this student..."
                    rows={3}
                    className="w-full border-0 rounded-xl px-3 py-2.5 text-sm text-gray-800
                               focus:ring-2 focus:ring-white outline-none resize-none"
                    autoFocus
                  />
                  <button
                    onClick={handleSubmitReview}
                    disabled={reviewSubmitting || !reviewText.trim()}
                    className="w-full py-2.5 bg-white text-gray-800 rounded-xl text-sm
                               font-semibold hover:bg-gray-100 disabled:opacity-50 transition-colors"
                  >
                    {reviewSubmitting ? 'Submitting…' : 'Submit Note'}
                  </button>
                </div>
              )}
            </div>

            {/* Scan Again */}
            <button
              onClick={handleScanAgain}
              className="w-full py-3.5 bg-white/20 hover:bg-white/30 active:scale-95
                         text-white font-bold text-base rounded-xl transition-all"
            >
              📷 Scan Next Student
            </button>
          </div>
        )
      })()}

      {/* ── SCANNER AREA (hidden when showing result card) ── */}
      {view !== 'result' && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          {view === 'closed' ? (
            <button
              onClick={() => setView('scanning')}
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
              {/* Error flash overlay */}
              {scanState === 'error' && (
                <div className="absolute inset-0 z-20 flex items-center justify-center bg-red-500/80">
                  <p className="text-white font-bold text-2xl">✗ Invalid QR</p>
                </div>
              )}
              <div id="qr-reader-scan" className="w-full" />
              <button
                onClick={handleClose}
                className="absolute top-2 right-2 z-10 bg-white rounded-full p-1.5 shadow-md"
              >
                <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          )}
        </div>
      )}

      {/* Recent scans */}
      {recentScans.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <p className="px-4 py-3 text-sm font-semibold text-gray-600 border-b">Recent Scans</p>
          <div className="divide-y">
            {recentScans.map((scan) => (
              <div key={scan.id} className="px-4 py-3 flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-800">{scan.name}</p>
                  <p className="text-xs text-gray-400">
                    {scan.seatNumber ? `Seat ${scan.seatNumber} · ` : ''}
                    {scan.time.toLocaleTimeString()}
                  </p>
                </div>
                <span className={`w-3 h-3 rounded-full ${scan.result === 'success' ? 'bg-green-500'
                  : scan.result === 'duplicate' ? 'bg-yellow-400' : 'bg-red-500'
                  }`} />
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
