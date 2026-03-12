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
  message: string
  time: Date
}

export default function VerifierScan() {
  const { assignments } = useAuthStore()
  const days = assignments?.map((a) => a.day) ?? ['day1']
  const [selectedDay, setSelectedDay] = useState<string>(days[0])
  const [scannerActive, setScannerActive] = useState(false)
  const [scanState, setScanState] = useState<ScanState>('idle')
  const [lastResult, setLastResult] = useState<StudentScanResult | null>(null)
  const [recentScans, setRecentScans] = useState<RecentScan[]>([])
  // Fix 6 — review state
  const [showReviewInput, setShowReviewInput] = useState(false)
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

  // Fix 5 — handleNextScan: manually reset state and restart camera
  const handleNextScan = () => {
    setScanState('idle')
    setLastResult(null)
    setShowReviewInput(false)
    setReviewText('')
    processingRef.current = false
    setScannerActive(true)  // re-triggers useEffect that starts the camera
  }

  // Fix 6 — handleSubmitReview
  const handleSubmitReview = async () => {
    const checkInId = lastResult?.checkInId
    const studentId = lastResult?.studentId
    const day = selectedDay
    if (!checkInId || !reviewText.trim()) return
    setReviewSubmitting(true)
    try {
      await addReview(checkInId, studentId ?? 0, day, reviewText.trim())
      toast.success('Review added')
      setReviewText('')
      setShowReviewInput(false)
    } catch {
      toast.error('Failed to add review')
    } finally {
      setReviewSubmitting(false)
    }
  }

  const handleScanSuccess = useCallback(
    async (qrData: string) => {
      if (processingRef.current) return
      processingRef.current = true

      try {
        const { data } = await scanQr(qrData, selectedDay)
        const isSuccess = data.success && !data.alreadyCheckedIn
        const isDuplicate = data.success && data.alreadyCheckedIn
        const isError = !data.success
        const state: ScanState = isSuccess ? 'success' : isDuplicate ? 'duplicate' : 'error'

        setLastResult(data)
        setScanState(state)
        setRecentScans((prev) => [
          {
            id: Date.now().toString(),
            name: data.name ?? 'Unknown',
            seatNumber: data.seatNumber || null,
            result: state,
            message: data.message ?? '',
            time: new Date(),
          },
          ...prev,
        ].slice(0, 10))

        if (isSuccess) refetchStats()

        // Fix 5 — stop camera on success or duplicate; keep running on error
        if (!isError) {
          await scannerRef.current?.stop()
          setScannerActive(false)
          // processingRef stays true until handleNextScan — card persists
        } else {
          // Error: auto-dismiss after 1500ms and let camera keep running
          setTimeout(() => {
            setScanState('idle')
            setLastResult(null)
            processingRef.current = false
          }, 1500)
        }
      } catch (err: any) {
        setLastResult(null)
        setScanState('error')
        setRecentScans((prev) => [
          {
            id: Date.now().toString(),
            name: 'Unknown',
            seatNumber: null,
            result: 'error' as ScanState,
            message: err?.response?.data?.message ?? 'Scan failed',
            time: new Date(),
          },
          ...prev,
        ].slice(0, 10))
        setTimeout(() => {
          setScanState('idle')
          processingRef.current = false
        }, 1500)
      }

    },
    [selectedDay, refetchStats]
  )

  useEffect(() => {
    if (!scannerActive) return
    const scanner = new Html5Qrcode('qr-reader-scan')
    scannerRef.current = scanner
    scanner
      .start(
        { facingMode: 'environment' },
        { fps: QR_FPS, qrbox: { width: QR_BOX_W, height: QR_BOX_H } },
        handleScanSuccess,
        () => { }
      )
      .catch(() => setScannerActive(false))
    return () => {
      scanner.stop().catch(() => { })
    }
  }, [scannerActive, handleScanSuccess])

  const overlayBg: Record<ScanState, string> = {
    idle: '',
    success: 'bg-green-500',
    duplicate: 'bg-yellow-400',
    error: 'bg-red-500',
  }

  const scanStateLabel: Record<ScanState, string> = {
    idle: '',
    success: '✓ Checked In!',
    duplicate: '⚠ Already Scanned',
    error: '✗ Invalid QR',
  }

  return (
    <div className="space-y-4">
      {/* Day selector */}
      {days.length > 1 && (
        <div className="flex gap-2">
          {days.map((d) => (
            <button
              key={d}
              onClick={() => setSelectedDay(d)}
              className={`flex-1 py-2 rounded-lg text-sm font-medium transition-colors ${selectedDay === d
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'bg-white border border-gray-200 text-gray-600 hover:border-indigo-300'
                }`}
            >
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
            <div
              className={`h-full rounded-full transition-all duration-500 ${stats.status === 'high' ? 'bg-green-500' :
                stats.status === 'medium' ? 'bg-yellow-400' : 'bg-red-500'
                }`}
              style={{ width: `${Math.min(stats.percentage, 100)}%` }}
            />
          </div>
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-400">{stats.percentage.toFixed(1)}% filled</span>
            <span className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${stats.status === 'high' ? 'text-green-700 bg-green-50' :
              stats.status === 'medium' ? 'text-yellow-700 bg-yellow-50' :
                'text-red-700 bg-red-50'
              }`}>{stats.status.toUpperCase()}</span>
          </div>
        </div>
      )}

      {/* Student info card (after scan) — Fix 5: persists until Next Scan is clicked */}
      {scanState !== 'idle' && lastResult && (
        <div className={`rounded-xl p-4 text-white ${scanState === 'success' ? 'bg-green-500' :
          scanState === 'duplicate' ? 'bg-yellow-400' : 'bg-red-500'
          }`}>
          <p className="font-bold text-xl">{scanStateLabel[scanState]}</p>
          {lastResult.name && <p className="text-lg font-semibold mt-1">{lastResult.name}</p>}
          {lastResult.regNo && <p className="text-sm opacity-80">{lastResult.regNo}</p>}
          {lastResult.degree && <p className="text-sm opacity-80">{lastResult.degree}</p>}
          {lastResult.seatNumber && (
            <p className="text-lg font-bold mt-2 bg-white/20 px-3 py-1 rounded-lg inline-block">
              Seat {lastResult.seatNumber}
            </p>
          )}

          {/* Fix 6 — Add Review toggle (only on success/duplicate) */}
          {(scanState === 'success' || scanState === 'duplicate') && (
            <>
              <button
                onClick={() => setShowReviewInput(v => !v)}
                className="w-full py-2 mt-3 text-sm text-white border border-white/40
                           rounded-lg hover:bg-white/10 transition-colors"
              >
                {showReviewInput ? '✕ Cancel Review' : '📝 Add Review'}
              </button>

              {showReviewInput && (
                <div className="mt-2 space-y-2">
                  <textarea
                    value={reviewText}
                    onChange={e => setReviewText(e.target.value)}
                    placeholder="Write your review..."
                    rows={3}
                    className="w-full border rounded-lg px-3 py-2 text-sm text-gray-800
                               focus:ring-2 focus:ring-indigo-500 outline-none resize-none"
                  />
                  <button
                    onClick={handleSubmitReview}
                    disabled={reviewSubmitting || !reviewText.trim()}
                    className="w-full py-2 bg-white text-indigo-600 rounded-lg text-sm
                               font-medium hover:bg-indigo-50 disabled:opacity-50
                               transition-colors"
                  >
                    {reviewSubmitting ? 'Submitting...' : 'Submit Review'}
                  </button>
                </div>
              )}
            </>
          )}

          {/* Fix 5 — Next Scan button (only on success/duplicate) */}
          {(scanState === 'success' || scanState === 'duplicate') && (
            <button
              onClick={handleNextScan}
              className="w-full mt-4 py-3 bg-white/20 text-white rounded-xl
                         font-semibold text-sm hover:bg-white/30
                         active:scale-95 transition-all"
            >
              Next Scan →
            </button>
          )}
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
            {scanState !== 'idle' && (
              <div className={`absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 ${overlayBg[scanState]} bg-opacity-90 transition-all`}>
                <p className="text-white font-bold text-2xl">{scanStateLabel[scanState]}</p>
                {lastResult?.name && <p className="text-white">{lastResult.name}</p>}
                {lastResult?.seatNumber && <p className="text-white opacity-80">Seat {lastResult.seatNumber}</p>}
              </div>
            )}
            <div id="qr-reader-scan" className="w-full" />
            <button
              onClick={() => { setScannerActive(false); scannerRef.current?.stop().catch(() => { }) }}
              className="absolute top-2 right-2 z-10 bg-white rounded-full p-1.5 shadow-md"
            >
              <svg className="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        )}
      </div>

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
                <span className={`w-3 h-3 rounded-full ${scan.result === 'success' ? 'bg-green-500' :
                  scan.result === 'duplicate' ? 'bg-yellow-400' : 'bg-red-500'
                  }`} />
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
