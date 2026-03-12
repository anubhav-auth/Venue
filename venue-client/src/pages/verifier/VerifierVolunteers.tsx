import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { markVolunteerAbsent } from '@/api/verifier'
import toast from 'react-hot-toast'
import { CheckCircle2, Circle, UserX } from 'lucide-react'
import api from '@/api/client'

interface Volunteer {
  studentId: number
  name: string
  regNo: string
  checkedIn: boolean
  checkInTime: string | null
}

export default function VerifierVolunteers() {
  const [day, setDay] = useState('day1')
  const qc = useQueryClient()

  const { data: volunteers = [], isLoading } = useQuery<Volunteer[]>({
    queryKey: ['verifierVolunteers', day],
    queryFn: () =>
      api
        .get<Volunteer[]>('/verifier/volunteers', { params: { day } })
        .then((r) => r.data),
    refetchInterval: 15_000,
  })

  const markAbsent = useMutation({
    mutationFn: (id: number) => markVolunteerAbsent(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['verifierVolunteers'] })
      toast.success('Marked as absent')
    },
    onError: () => toast.error('Failed to mark absent'),
  })

  const checkedIn = volunteers.filter((v) => v.checkedIn).length
  const absent = volunteers.length - checkedIn

  return (
    <div className="space-y-4">
      {/* Day tabs */}
      <div className="flex gap-2">
        {['day1', 'day2'].map((d) => (
          <button
            key={d}
            onClick={() => setDay(d)}
            className={`flex-1 py-2 rounded-lg text-sm font-medium transition-colors ${
              day === d
                ? 'bg-indigo-600 text-white'
                : 'bg-white border border-gray-200 text-gray-600'
            }`}
          >
            {d === 'day1' ? 'Day 1' : 'Day 2'}
          </button>
        ))}
      </div>

      {/* Summary */}
      <div className="grid grid-cols-3 gap-3 text-center">
        {[
          { label: 'Present', value: checkedIn, color: 'text-green-600' },
          { label: 'Absent',  value: absent,    color: 'text-red-500'   },
          { label: 'Total',   value: volunteers.length, color: 'text-gray-700' },
        ].map(({ label, value, color }) => (
          <div key={label} className="bg-white rounded-xl border p-3">
            <p className={`text-2xl font-bold ${color}`}>{value}</p>
            <p className="text-xs text-gray-400 mt-0.5">{label}</p>
          </div>
        ))}
      </div>

      {/* Volunteer list */}
      {isLoading ? (
        <div className="text-center py-10 text-gray-400 text-sm">Loading…</div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <p className="px-4 py-3 text-sm font-semibold text-gray-600 border-b">
            Volunteers ({volunteers.length})
          </p>
          {volunteers.length === 0 ? (
            <p className="px-4 py-8 text-center text-gray-400 text-sm">No volunteers assigned</p>
          ) : (
            <div className="divide-y">
              {volunteers.map((v) => (
                <div key={v.studentId} className="px-4 py-3 flex items-center gap-3">
                  {v.checkedIn ? (
                    <CheckCircle2 className="text-green-500 flex-shrink-0" size={18} />
                  ) : (
                    <Circle className="text-gray-300 flex-shrink-0" size={18} />
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-800 truncate">{v.name}</p>
                    <p className="text-xs text-gray-400">
                      {v.regNo}
                      {v.checkedIn && v.checkInTime
                        ? ` · ${new Date(v.checkInTime).toLocaleTimeString()}`
                        : ''}
                    </p>
                  </div>
                  {!v.checkedIn && (
                    <button
                      onClick={() => {
                        if (confirm(`Mark ${v.name} as absent?`))
                          markAbsent.mutate(v.studentId)
                      }}
                      className="p-1.5 text-gray-400 hover:text-red-500 transition-colors"
                      title="Mark absent"
                    >
                      <UserX size={16} />
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
