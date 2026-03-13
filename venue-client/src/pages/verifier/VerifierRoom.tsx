import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getMyRoom } from '@/api/verifier'
import { MapPin, CheckCircle2, Circle } from 'lucide-react'

export default function VerifierRoom() {
  const [day, setDay] = useState<'day1' | 'day2'>('day1')

  const { data, isLoading, error } = useQuery({
    queryKey: ['verifierMyRoom', day],
    queryFn: () => getMyRoom(day).then(r => r.data),
    refetchInterval: 15000,
  })

  if (isLoading)
    return (
      <div className="flex items-center justify-center py-20 text-gray-400">
        Loading room…
      </div>
    )

  if (error || !data)
    return (
      <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-6 text-center">
        <MapPin className="mx-auto mb-2 text-yellow-400" size={28} />
        <p className="text-yellow-700 font-medium">No room assigned yet</p>
        <p className="text-sm text-yellow-600 mt-1">
          Contact your admin to assign you a room.
        </p>
      </div>
    )

  const { roomName, building, floor, capacity, seats = [], overflow = [] } = data as any
  const checkedInCount = [...seats, ...overflow].filter((s: any) => s.checkedIn).length

  return (
    <div className="space-y-4">

      {/* Day toggle */}
      <div className="flex gap-2">
        {(['day1', 'day2'] as const).map(d => (
          <button
            key={d}
            onClick={() => setDay(d)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
              day === d
                ? 'bg-indigo-600 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            {d === 'day1' ? 'Day 1' : 'Day 2'}
          </button>
        ))}
      </div>

      {/* Room header */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-4">
        <div className="flex items-start gap-3">
          <div className="p-2 bg-indigo-100 rounded-lg">
            <MapPin className="text-indigo-600" size={20} />
          </div>
          <div>
            <h2 className="font-semibold text-gray-800 text-lg">{roomName}</h2>
            <p className="text-sm text-gray-400">{building} · Floor {floor}</p>
          </div>
        </div>
        <div className="grid grid-cols-3 gap-3 mt-4 text-center">
          {[
            { label: 'Checked In', value: checkedInCount,            color: 'text-green-600'  },
            { label: 'Remaining',  value: capacity - checkedInCount, color: 'text-orange-500' },
            { label: 'Capacity',   value: capacity,                  color: 'text-gray-700'   },
          ].map(({ label, value, color }) => (
            <div key={label} className="bg-gray-50 rounded-lg py-2">
              <p className={`text-xl font-bold ${color}`}>{value}</p>
              <p className="text-xs text-gray-400">{label}</p>
            </div>
          ))}
        </div>
        <div className="mt-3 h-2 bg-gray-100 rounded-full overflow-hidden">
          <div
            className="h-full bg-indigo-500 rounded-full transition-all duration-500"
            style={{ width: `${Math.min((checkedInCount / Math.max(capacity, 1)) * 100, 100)}%` }}
          />
        </div>
      </div>

      {/* Seat list */}
      {seats.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <p className="px-4 py-3 text-sm font-semibold text-gray-600 border-b">
            Assigned Seats ({seats.length})
          </p>
          <div className="divide-y max-h-[400px] overflow-y-auto">
            {(seats as any[]).map((s: any) => (
              <div key={s.studentId} className="px-4 py-3 flex items-center gap-3">
                {s.checkedIn
                  ? <CheckCircle2 className="text-green-500 flex-shrink-0" size={18} />
                  : <Circle className="text-gray-300 flex-shrink-0" size={18} />
                }
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-800 truncate">{s.name}</p>
                  <p className="text-xs text-gray-400">{s.regNo}</p>
                </div>
                <span className="text-xs font-mono bg-indigo-50 text-indigo-700 px-2 py-0.5 rounded">
                  {s.seatNumber}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Overflow */}
      {overflow.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <p className="px-4 py-3 text-sm font-semibold text-gray-600 border-b">
            Overflow ({overflow.length})
          </p>
          <div className="divide-y max-h-48 overflow-y-auto">
            {(overflow as any[]).map((s: any) => (
              <div key={s.studentId} className="px-4 py-3 flex items-center gap-3">
                {s.checkedIn
                  ? <CheckCircle2 className="text-green-500 flex-shrink-0" size={18} />
                  : <Circle className="text-gray-300 flex-shrink-0" size={18} />
                }
                <div className="flex-1">
                  <p className="text-sm font-medium text-gray-800">{s.name}</p>
                  <p className="text-xs text-gray-400">{s.regNo}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

    </div>
  )
}