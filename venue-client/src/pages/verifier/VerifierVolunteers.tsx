// src/pages/VerifierVolunteers.tsx
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { markVolunteerAbsent } from '../../api/verifier';
import api from '../../api/client';
import toast from 'react-hot-toast';
import { UserCheck, UserX } from 'lucide-react';

interface VolunteerRow {
  studentId: number;
  name: string;
  regNo: string;
  checkedIn: boolean;
  checkInTime: string | null;
}

const getVolunteersForRoom = (day: string) =>
  api.get<VolunteerRow[]>('/verifier/volunteers', { params: { day } }).then(r => r.data);

export default function VerifierVolunteers() {
  const [day, setDay] = useState<'day1' | 'day2'>('day1');
  const qc = useQueryClient();

  const { data: volunteers = [], isLoading } = useQuery({
    queryKey: ['verifier-volunteers', day],
    queryFn: () => getVolunteersForRoom(day),
  });

  const markAbsent = useMutation({
    mutationFn: (id: number) => markVolunteerAbsent(id),
    onSuccess: () => {
      toast.success('Marked as absent');
      qc.invalidateQueries({ queryKey: ['verifier-volunteers', day] });
    },
    onError: (e: any) => toast.error(e.response?.data?.error ?? 'Failed'),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-gray-800">Volunteers in Your Room</h1>
        <div className="flex gap-2">
          {(['day1', 'day2'] as const).map(d => (
            <button
              key={d}
              onClick={() => setDay(d)}
              className={`px-4 py-1.5 rounded-full text-sm font-medium ${
                day === d ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {d === 'day1' ? 'Day 1' : 'Day 2'}
            </button>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-xl border overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-xs text-gray-500 uppercase">
            <tr>
              {['Reg No', 'Name', 'Status', 'Check-in Time', 'Action'].map(h => (
                <th key={h} className="px-4 py-3 text-left font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              [...Array(5)].map((_, i) => (
                <tr key={i} className="animate-pulse">
                  {[...Array(5)].map((_, j) => (
                    <td key={j} className="px-4 py-3">
                      <div className="h-4 bg-gray-200 rounded w-24" />
                    </td>
                  ))}
                </tr>
              ))
            ) : volunteers.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-10 text-center text-gray-400">
                  No volunteers assigned to your room for {day === 'day1' ? 'Day 1' : 'Day 2'}
                </td>
              </tr>
            ) : (
              volunteers.map(v => (
                <tr key={v.studentId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-600">{v.regNo}</td>
                  <td className="px-4 py-3 font-medium text-gray-800">{v.name}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                      v.checkedIn
                        ? 'bg-green-100 text-green-700'
                        : 'bg-yellow-100 text-yellow-700'
                    }`}>
                      {v.checkedIn ? '✓ Present' : 'Pending'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-400">
                    {v.checkInTime
                      ? new Date(v.checkInTime).toLocaleTimeString()
                      : '—'}
                  </td>
                  <td className="px-4 py-3">
                    {!v.checkedIn && (
                      <button
                        onClick={() => markAbsent.mutate(v.studentId)}
                        disabled={markAbsent.isPending}
                        className="flex items-center gap-1 px-3 py-1 text-xs bg-red-100 text-red-600 rounded-lg hover:bg-red-200 disabled:opacity-40"
                      >
                        <UserX size={12} /> Mark Absent
                      </button>
                    )}
                    {v.checkedIn && (
                      <span className="flex items-center gap-1 text-xs text-green-600">
                        <UserCheck size={12} /> Verified
                      </span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
