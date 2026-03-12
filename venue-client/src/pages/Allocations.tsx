// src/pages/Allocations.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { triggerAllocation, getAllocations } from '@/api/allocations'
import toast from 'react-hot-toast'
import { Play, RefreshCw } from 'lucide-react'
import api from '@/api/client'

export default function Allocations() {
  const [day, setDay] = useState<'day1' | 'day2'>('day1')
  const [page, setPage] = useState(0)
  const qc = useQueryClient()

  const { data: alloc } = useQuery({
    queryKey: ['allocations', day, page],
    queryFn: () => getAllocations({ day, page, size: 50 }).then(r => r.data)
  })

  const run = useMutation({
    mutationFn: () => triggerAllocation(day),
    onSuccess: (r: any) => {
      toast.success(`Allocated: Day1=${r.data.day1Count}, Day2=${r.data.day2Count}, Overflow=${r.data.overflowCount}`)
      qc.invalidateQueries({ queryKey: ['allocations'] })
    },
    onError: (e: any) => {
      if (e.response?.status === 409) toast.error('Already allocated. Use Re-allocate.')
      else toast.error('Allocation failed')
    }
  })

  const rerun = useMutation({
    mutationFn: () => api.post('admin/allocate/clear'),
    onSuccess: () => { run.mutate(); toast.success('Re-allocating…') }
  })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Seat Allocation</h1>
        <div className="flex gap-2">
          <button onClick={() => { if (confirm('Run allocation for all days?')) run.mutate() }}
            disabled={run.isPending}
            className="flex items-center gap-2 bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-indigo-700 disabled:opacity-60">
            <Play size={14} /> Run Allocation
          </button>
          <button onClick={() => { if (confirm('⚠️ This will CLEAR all existing allocations and re-run. Are you sure?') && confirm('This is irreversible. Confirm?')) rerun.mutate() }}
            className="flex items-center gap-2 bg-orange-500 text-white px-4 py-2 rounded-lg text-sm hover:bg-orange-600">
            <RefreshCw size={14} /> Re-allocate
          </button>
        </div>
      </div>

      {/* Day tabs */}
      <div className="flex gap-2">
        {(['day1', 'day2'] as const).map(d => (
          <button key={d} onClick={() => { setDay(d); setPage(0) }}
            className={`px-4 py-1.5 rounded-full text-sm font-medium ${day === d ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-600'}`}>
            {d === 'day1' ? 'Day 1' : 'Day 2'}
            {alloc && <span className="ml-1 text-xs opacity-70">({alloc.totalElements})</span>}
          </button>
        ))}
      </div>

      {/* Allocations table */}
      <div className="bg-white rounded-xl border overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-xs text-gray-500 uppercase">
            <tr>{['RegNo', 'Name', 'Branch', 'Room', 'Seat', 'Status'].map(h => <th key={h} className="px-4 py-3 text-left">{h}</th>)}</tr>
          </thead>
          <tbody className="divide-y">
            {alloc?.content.map(a => (
              <tr key={a.assignmentId} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-mono text-xs">{a.regNo}</td>
                <td className="px-4 py-3">{a.name}</td>
                <td className="px-4 py-3 text-gray-500">{a.degree}</td>
                <td className="px-4 py-3">{a.roomName}</td>
                <td className="px-4 py-3">{a.seatNumber ?? '—'}</td>
                <td className="px-4 py-3">
                  {a.overflow
                    ? <span className="px-2 py-0.5 text-xs rounded-full bg-yellow-100 text-yellow-700">Overflow</span>
                    : <span className="px-2 py-0.5 text-xs rounded-full bg-green-100 text-green-700">Seated</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {/* Pagination */}
        {alloc && alloc.totalPages > 1 && (
          <div className="flex justify-end gap-2 p-4">
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1 text-sm border rounded disabled:opacity-40">Prev</button>
            <span className="text-sm py-1">{page + 1} / {alloc.totalPages}</span>
            <button disabled={alloc.last} onClick={() => setPage(p => p + 1)} className="px-3 py-1 text-sm border rounded disabled:opacity-40">Next</button>
          </div>
        )}
      </div>
    </div>
  )
}
