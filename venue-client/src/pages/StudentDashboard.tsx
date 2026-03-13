import { useQuery } from '@tanstack/react-query'
import { MapPin, Download, QrCode, CheckCircle2, User, Hash, Building2, Armchair } from 'lucide-react'
import { useAuthStore } from '../store/authStore'
import { getStudentAssignment, downloadStudentQr } from '../api/studentPortal'
import toast from 'react-hot-toast'
import { useEffect, useState } from 'react'

export default function StudentDashboard() {
  const { username, logout } = useAuthStore()
  const [qrUrl, setQrUrl] = useState<string | null>(null)

  const { data: assignment, isLoading, error } = useQuery({
    queryKey: ['studentAssignment'],
    queryFn: () => getStudentAssignment().then(r => r.data),
    refetchInterval: 10000,
  })

  // ✅ Fetch QR as soon as assignment loads, regardless of day
  // day is only needed after scan — before scan we use student-level QR
  useEffect(() => {
    if (!assignment) return
    downloadStudentQr(assignment.day ?? undefined)
      .then(r => setQrUrl(URL.createObjectURL(new Blob([r.data]))))
      .catch(() => {})
  }, [!!assignment]) // ✅ fires once when assignment transitions null → loaded

  const handleDownloadQR = () => {
    if (!qrUrl) return
    const a = document.createElement('a')
    a.href = qrUrl
    a.download = `venue-pass-${username}.png`
    a.click()
    toast.success('QR Code downloaded!')
  }

  if (isLoading) return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center text-gray-500">
      Loading your pass...
    </div>
  )

  if (error) return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center gap-3">
      <p className="text-red-500">Failed to load seat assignment.</p>
      <button onClick={logout} className="text-indigo-600 underline">Sign Out</button>
    </div>
  )

  const isCheckedIn = assignment?.checkedIn === true
  const hasRoom = !!assignment?.roomName  // has been scanned + assigned a room

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Header */}
      <div className="bg-white border-b px-4 py-3 flex items-center justify-between">
        <div>
          <h1 className="font-semibold text-gray-800">My Venue Pass</h1>
          <p className="text-xs text-gray-400">{assignment?.name ?? username}</p>
        </div>
        <button onClick={logout} className="text-sm text-red-500 hover:text-red-700 font-medium">
          Logout
        </button>
      </div>

      <div className="max-w-sm mx-auto w-full px-4 py-8 space-y-5">

        {/* ✅ Student Info Card — always visible */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 space-y-3">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-full bg-indigo-100 flex items-center justify-center">
              <User size={22} className="text-indigo-600" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-lg font-bold text-gray-900 truncate">{assignment?.name ?? username}</p>
              <div className="flex items-center gap-1.5 mt-0.5">
                <Hash size={12} className="text-gray-400" />
                <p className="text-sm font-mono text-gray-500">{assignment?.regNo ?? '—'}</p>
              </div>
            </div>
          </div>

          {/* Status badge */}
          <div className="flex items-center gap-2">
            <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold ${
              isCheckedIn
                ? 'bg-green-100 text-green-700'
                : hasRoom
                  ? 'bg-indigo-100 text-indigo-700'
                  : 'bg-gray-100 text-gray-500'
            }`}>
              {isCheckedIn ? (
                <><CheckCircle2 size={12} /> Checked In</>
              ) : hasRoom ? (
                <><MapPin size={12} /> Room Assigned</>
              ) : (
                'Awaiting Check-in'
              )}
            </span>
            {assignment?.day && (
              <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-blue-50 text-blue-600">
                {assignment.day === 'day1' ? 'Day 1' : 'Day 2'}
              </span>
            )}
          </div>
        </div>

        {/* ✅ QR always shown — student needs this to get scanned */}
        <div className="bg-white rounded-xl border p-6 flex flex-col items-center gap-4">
          <div className="flex items-center gap-2 text-sm font-semibold text-gray-700">
            <QrCode size={16} />
            Your Entry QR
          </div>
          {qrUrl ? (
            <img src={qrUrl} alt="Entry QR Code" className="w-48 h-48 rounded-lg" />
          ) : (
            <div className="w-48 h-48 bg-gray-100 rounded-lg flex items-center justify-center text-gray-400 text-xs">
              Loading QR...
            </div>
          )}
          <button
            onClick={handleDownloadQR}
            disabled={!qrUrl}
            className="flex items-center gap-2 px-4 py-2 text-sm border rounded-lg hover:bg-gray-50 disabled:opacity-40"
          >
            <Download size={14} />
            Download Pass
          </button>
        </div>

        {/* ✅ Pre-scan state: show helpful message */}
        {!hasRoom ? (
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 text-center">
            <p className="text-blue-700 font-medium text-sm">Show this QR at the venue entrance</p>
            <p className="text-xs text-blue-500 mt-1">Your seat will be assigned when you check in</p>
          </div>
        ) : (
          /* Post-scan: room & seat assignment card */
          <div className={`rounded-xl p-5 border transition-colors duration-500 ${
            isCheckedIn ? 'bg-green-50 border-green-200' : 'bg-indigo-50 border-indigo-100'
          }`}>
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Your Assignment</p>

            {/* Room */}
            <div className="flex items-center gap-3 mb-2">
              <div className={`p-2.5 rounded-lg ${
                isCheckedIn ? 'bg-green-100 text-green-600' : 'bg-indigo-100 text-indigo-600'
              }`}>
                <Building2 size={20} />
              </div>
              <div>
                <h2 className="text-xl font-black text-gray-900">{assignment!.roomName}</h2>
                <p className="text-xs text-gray-500">
                  Building {assignment!.building} · Floor {assignment!.floor}
                </p>
              </div>
            </div>

            {/* Seat */}
            {assignment!.seatNumber && (
              <div className="flex items-center gap-3 mt-3 pt-3 border-t border-gray-200/60">
                <div className={`p-2.5 rounded-lg ${
                  isCheckedIn ? 'bg-green-100 text-green-600' : 'bg-indigo-100 text-indigo-600'
                }`}>
                  <Armchair size={20} />
                </div>
                <div>
                  <p className="text-lg font-bold text-gray-900">Seat {assignment!.seatNumber}</p>
                  <p className="text-xs text-gray-500">Your assigned seat</p>
                </div>
              </div>
            )}

            {/* Checked in time */}
            {isCheckedIn && assignment!.checkInTime && (
              <p className="text-xs text-green-600 mt-3 pt-2 border-t border-green-200/60">
                ✓ Checked in at {new Date(assignment!.checkInTime).toLocaleTimeString()}
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
