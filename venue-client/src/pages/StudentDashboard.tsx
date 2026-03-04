import { useQuery } from '@tanstack/react-query'
import { LogOut, MapPin, Download, QrCode } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { getStudentAssignment, downloadStudentQr } from '@/api/studentPortal'
import toast from 'react-hot-toast'
import { useEffect, useState } from 'react'

export default function StudentDashboard() {
  const { username, logout } = useAuthStore()
  const [qrUrl, setQrUrl] = useState<string | null>(null)

  const { data: assignment, isLoading, error } = useQuery({
    queryKey: ['studentAssignment'],
    queryFn: () => getStudentAssignment().then(r => r.data),
  })

  // Fetch QR as blob once we know the day
  useEffect(() => {
    if (!assignment) return
    downloadStudentQr(assignment.day ?? undefined)
      .then(r => setQrUrl(URL.createObjectURL(new Blob([r.data]))))
      .catch(() => {})
  }, [assignment?.day])

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

  const hasAssignment = !!assignment?.roomName

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
        {!hasAssignment ? (
          <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-6 text-center">
            <p className="text-yellow-700 font-medium">No seat assigned yet</p>
            <p className="text-sm text-yellow-600 mt-1">
              Check back after allocation is complete
            </p>
          </div>
        ) : (
          <>
            {/* Seat Card */}
            <div className="bg-indigo-50 rounded-xl p-6 border border-indigo-100">
              <div className="flex items-start gap-4">
                <div className="p-3 bg-indigo-100 rounded-lg text-indigo-600">
                  <MapPin size={24} />
                </div>
                <div>
                  <p className="text-xs font-bold text-indigo-900 uppercase tracking-wider">
                    {assignment.day === 'day1' ? 'Day 1' : 'Day 2'}
                  </p>
                  <h2 className="text-2xl font-black text-gray-900 mt-1">{assignment.roomName}</h2>
                  <p className="text-sm text-gray-600 mt-1">
                    Building {assignment.building} · Floor {assignment.floor}
                  </p>
                </div>
              </div>
              <div className="mt-5 inline-block bg-white px-5 py-3 rounded-lg border border-indigo-200 shadow-sm">
                <span className="text-xs text-gray-500 uppercase font-semibold">Seat Number</span>
                <p className="text-3xl font-black text-indigo-600">
                  {assignment.seatNumber ?? 'Overflow'}
                </p>
              </div>
            </div>

            {/* QR Code */}
            <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6 flex flex-col items-center gap-4">
              <p className="text-sm font-semibold text-gray-600 flex items-center gap-2">
                <QrCode size={16} /> Your Entry QR
              </p>
              {qrUrl ? (
                <img src={qrUrl} alt="Entry QR" className="w-56 h-56 rounded-lg" />
              ) : (
                <div className="w-56 h-56 bg-gray-100 rounded-lg animate-pulse" />
              )}
              <button
                onClick={handleDownloadQR}
                disabled={!qrUrl}
                className="w-full flex items-center justify-center gap-2 py-2.5 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-40 transition-colors"
              >
                <Download size={16} /> Download QR Pass
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}