import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { getStudentsOverview, StudentOverview } from '../api/students'
import {
  uploadVolunteerCsv, getVolunteers,
  adminScanVolunteer, promoteVolunteer, getVerifiers,
  demoteVerifier, pollVolunteerUploadStatus
} from '../api/volunteers'
import { updateVerifier } from '../api/verifier'
import { getRooms } from '../api/rooms'
import CsvUploadZone from '../components/CsvUploadZone'
import { useCsvUpload } from '../hooks/useCsvUpload'
import { useDebounce } from '../hooks/useDebounce'
import toast from 'react-hot-toast'
import { Html5Qrcode } from 'html5-qrcode'
import { Camera, CameraOff, UserCheck, UserX, Search } from 'lucide-react'

type Tab = 'students' | 'volunteers' | 'verifiers'

export default function People() {
  const [tab, setTab] = useState<Tab>('students')
  const qc = useQueryClient()

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-gray-800">People</h1>
      <div className="flex gap-2">
        {(['students', 'volunteers', 'verifiers'] as Tab[]).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium capitalize ${
              tab === t ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'students'   && <StudentsTab />}
      {tab === 'volunteers' && <VolunteersTab qc={qc} />}
      {tab === 'verifiers'  && <VerifiersTab qc={qc} />}
    </div>
  )
}

// ── Students overview ────────────────────────────────────────────────────────
function StudentsTab() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const debouncedSearch = useDebounce(search, 300)

  const { data, isLoading } = useQuery({
    queryKey: ['students-overview', debouncedSearch, page],
    queryFn: () => getStudentsOverview(debouncedSearch, page).then(r => r.data),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-sm text-gray-400">{data?.totalElements ?? 0} total students</span>
      </div>

      <div className="relative">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          value={search}
          onChange={e => { setSearch(e.target.value); setPage(0) }}
          placeholder="Search by name, reg no, or branch..."
          className="w-full pl-9 pr-4 py-2.5 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"
        />
      </div>

      <div className="bg-white rounded-xl border overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-xs text-gray-500 uppercase">
            <tr>
              {['Reg No', 'Name', 'Branch', 'Role', 'Day 1 Room', 'Day 2 Room'].map(h => (
                <th key={h} className="px-4 py-3 text-left font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading ? (
              [...Array(8)].map((_, i) => (
                <tr key={i} className="animate-pulse">
                  {[...Array(6)].map((_, j) => (
                    <td key={j} className="px-4 py-3">
                      <div className="h-4 bg-gray-200 rounded w-24" />
                    </td>
                  ))}
                </tr>
              ))
            ) : data?.content.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-10 text-center text-gray-400">No students found</td>
              </tr>
            ) : (
              data?.content.map((s: StudentOverview) => {
                const day1 = s.assignments.find(a => a.day === 'day1')
                const day2 = s.assignments.find(a => a.day === 'day2')
                return (
                  <tr key={s.studentId} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 font-mono text-xs text-gray-600">{s.regNo}</td>
                    <td className="px-4 py-3 font-medium text-gray-800">{s.name}</td>
                    <td className="px-4 py-3">
                      <span className="px-2 py-0.5 text-xs rounded-full bg-indigo-50 text-indigo-700 font-medium">
                        {s.degree}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${
                        s.role === 'VOLUNTEER' ? 'bg-emerald-50 text-emerald-700' : 'bg-gray-100 text-gray-600'
                      }`}>
                        {s.role}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      {day1 ? (
                        <span title={`${day1.building}, ${day1.floor} — Seat ${day1.seatNumber ?? 'TBD'}`}>
                          {day1.roomName}
                          {day1.seatNumber && <span className="ml-1 text-xs text-gray-400">#{day1.seatNumber}</span>}
                        </span>
                      ) : <span className="text-gray-300">—</span>}
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      {day2 ? (
                        <span title={`${day2.building}, ${day2.floor} — Seat ${day2.seatNumber ?? 'TBD'}`}>
                          {day2.roomName}
                          {day2.seatNumber && <span className="ml-1 text-xs text-gray-400">#{day2.seatNumber}</span>}
                        </span>
                      ) : <span className="text-gray-300">—</span>}
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>

        {data && data.totalPages > 1 && (
          <div className="flex justify-end gap-2 p-4 border-t">
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
              className="px-3 py-1 text-sm border rounded disabled:opacity-40">Prev</button>
            <span className="text-sm py-1 text-gray-500">{page + 1} / {data.totalPages}</span>
            <button disabled={data.last} onClick={() => setPage(p => p + 1)}
              className="px-3 py-1 text-sm border rounded disabled:opacity-40">Next</button>
          </div>
        )}
      </div>
    </div>
  )
}

// ── Volunteers ───────────────────────────────────────────────────────────────
function VolunteersTab({ qc }: { qc: ReturnType<typeof useQueryClient> }) {
  const [promoteModal, setPromoteModal] = useState<{ open: boolean; id?: number; name?: string }>({ open: false })
  const [assignments, setAssignments] = useState<{ day: string; roomId: number }[]>([{ day: 'day1', roomId: 0 }])
  const [scanActive, setScanActive] = useState(false)
  const scannerRef = useRef<Html5Qrcode | null>(null)

  const volunteers_upload = useCsvUpload(uploadVolunteerCsv, pollVolunteerUploadStatus, ['volunteers'])

  const { data: volunteers = [] } = useQuery({ queryKey: ['volunteers'], queryFn: () => getVolunteers().then(r => r.data) })
  const { data: day1Rooms = [] }  = useQuery({ queryKey: ['rooms', 'day1'], queryFn: () => getRooms('day1').then(r => r.data) })
  const { data: day2Rooms = [] }  = useQuery({ queryKey: ['rooms', 'day2'], queryFn: () => getRooms('day2').then(r => r.data) })

  const scan = useMutation({
    mutationFn: (qrData: string) => adminScanVolunteer(qrData),
    onSuccess: r => { toast.success(`✅ Scanned: ${r.data.name}`); qc.invalidateQueries({ queryKey: ['volunteers'] }) },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Scan failed'),
  })

  const promote = useMutation({
    mutationFn: ({ id, a }: { id: number; a: typeof assignments }) => promoteVolunteer(id, a),
    onSuccess: () => {
      toast.success('Promoted to verifier!')
      qc.invalidateQueries({ queryKey: ['volunteers'] })
      qc.invalidateQueries({ queryKey: ['verifiers'] })
      setPromoteModal({ open: false })
    },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Promotion failed'),
  })

  const startScan = async () => {
    setScanActive(true)
    const scanner = new Html5Qrcode('admin-qr-reader')
    scannerRef.current = scanner
    await scanner.start({ facingMode: 'environment' }, { fps: 10, qrbox: 250 },
      text => { scan.mutate(text); stopScan() }, () => {})
  }
  const stopScan = async () => {
    setScanActive(false)
    try { await scannerRef.current?.stop() } catch {}
  }

  return (
    <div className="space-y-6">

      {/* Upload + Scanner side by side */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border p-6 space-y-3">
          <h2 className="font-semibold text-sm">Volunteer CSV Upload</h2>
          <p className="text-xs text-gray-400">Required columns: Full Name, Regd. No, BRANCH. QR generated automatically.</p>
          <CsvUploadZone label="Volunteers" onUpload={volunteers_upload.upload} />
          {volunteers_upload.status === 'processing' && <p className="text-xs text-indigo-500 animate-pulse">⏳ Processing…</p>}
          {volunteers_upload.status === 'done'       && <p className="text-xs text-green-600">{volunteers_upload.msg}</p>}
          {volunteers_upload.status === 'error'      && <p className="text-xs text-red-500">{volunteers_upload.msg}</p>}
        </div>

        <div className="bg-white rounded-xl border p-6 space-y-3">
          <h2 className="font-semibold text-sm">Scan Volunteer QR (Admin Bootstrap)</h2>
          <div id="admin-qr-reader" className={scanActive ? 'w-full max-w-xs' : 'hidden'} />
          <button onClick={scanActive ? stopScan : startScan}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium ${
              scanActive ? 'bg-red-100 text-red-600' : 'bg-indigo-600 text-white hover:bg-indigo-700'
            }`}>
            {scanActive ? <><CameraOff size={14} /> Stop Scanner</> : <><Camera size={14} /> Open Scanner</>}
          </button>
        </div>
      </div>

      {/* Volunteers table */}
      <div className="bg-white rounded-xl border overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-xs text-gray-500 uppercase">
            <tr>{['RegNo', 'Name', 'Email', 'Day 1', 'Day 2', 'Status', ''].map(h =>
              <th key={h} className="px-4 py-3 text-left">{h}</th>
            )}</tr>
          </thead>
          <tbody className="divide-y">
            {volunteers.length === 0
              ? <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-400">No volunteers imported yet</td></tr>
              : volunteers.map(v => (
                <tr key={v.studentId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs">{v.regNo}</td>
                  <td className="px-4 py-3">{v.name}</td>
                  <td className="px-4 py-3 text-xs text-gray-400">{v.email}</td>
                  <td className="px-4 py-3">{v.day1Attended ? '✅' : '—'}</td>
                  <td className="px-4 py-3">{v.day2Attended ? '✅' : '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                      v.isPromoted ? 'bg-purple-100 text-purple-700' : 'bg-gray-100 text-gray-600'
                    }`}>
                      {v.isPromoted ? 'Promoted' : 'Volunteer'}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <button
                      disabled={!v.canPromote || v.isPromoted}
                      onClick={() => { setAssignments([{ day: 'day1', roomId: 0 }]); setPromoteModal({ open: true, id: v.studentId, name: v.name }) }}
                      className="flex items-center gap-1 px-3 py-1 text-xs bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed">
                      <UserCheck size={12} /> {v.isPromoted ? 'Promoted' : 'Promote'}
                    </button>
                  </td>
                </tr>
              ))
            }
          </tbody>
        </table>
      </div>

      {promoteModal.open && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md p-6 space-y-4">
            <h2 className="font-semibold">Promote {promoteModal.name} to Verifier</h2>
            <p className="text-xs text-gray-500">Assign room(s) this verifier will manage:</p>
            <div className="space-y-3">
              {assignments.map((a, i) => (
                <div key={i} className="grid grid-cols-2 gap-2">
                  <select value={a.day}
                    onChange={e => setAssignments(p => p.map((x, j) => j === i ? { ...x, day: e.target.value } : x))}
                    className="border rounded-lg px-3 py-2 text-sm">
                    <option value="day1">Day 1</option>
                    <option value="day2">Day 2</option>
                  </select>
                  <select value={a.roomId}
                    onChange={e => setAssignments(p => p.map((x, j) => j === i ? { ...x, roomId: +e.target.value } : x))}
                    className="border rounded-lg px-3 py-2 text-sm">
                    <option value={0}>Select room</option>
                    {(a.day === 'day1' ? day1Rooms : day2Rooms).map(r =>
                      <option key={r.id} value={r.id}>{r.roomName}</option>
                    )}
                  </select>
                </div>
              ))}
              <button onClick={() => setAssignments(p => [...p, { day: 'day1', roomId: 0 }])}
                className="text-sm text-indigo-600 hover:underline">+ Add another day</button>
            </div>
            <div className="flex gap-3 justify-end pt-2">
              <button onClick={() => setPromoteModal({ open: false })}
                className="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50">Cancel</button>
              <button
                onClick={() => promote.mutate({ id: promoteModal.id!, a: assignments.filter(x => x.roomId > 0) })}
                disabled={promote.isPending || assignments.every(x => x.roomId === 0)}
                className="px-4 py-2 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-60">
                {promote.isPending ? 'Promoting…' : 'Confirm Promote'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Verifiers ────────────────────────────────────────────────────────────────
function VerifiersTab({ qc }: { qc: ReturnType<typeof useQueryClient> }) {
  const [editMap, setEditMap] = useState<Record<number, { isTeamLead: boolean; assignedRoomId: number | null }>>({})

  const { data: verifiers = [] } = useQuery({ queryKey: ['verifiers'], queryFn: () => getVerifiers().then(r => r.data) })
  const { data: day1Rooms = [] }  = useQuery({ queryKey: ['rooms', 'day1'], queryFn: () => getRooms('day1').then(r => r.data) })
  const { data: day2Rooms = [] }  = useQuery({ queryKey: ['rooms', 'day2'], queryFn: () => getRooms('day2').then(r => r.data) })

  const allRooms = [...day1Rooms, ...day2Rooms]
  const getEdit  = (v: typeof verifiers[number]) => editMap[v.id] ?? { isTeamLead: v.isTeamLead, assignedRoomId: v.assignedRoomId }

  const demote = useMutation({
    mutationFn: (id: number) => demoteVerifier(id),
    onSuccess: () => { toast.success('Demoted'); qc.invalidateQueries({ queryKey: ['verifiers'] }); qc.invalidateQueries({ queryKey: ['volunteers'] }) },
  })

  const updateTL = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: { isTeamLead: boolean; assignedRoomId: number | null } }) =>
      updateVerifier(id, payload),
    onSuccess: () => { toast.success('Verifier updated'); qc.invalidateQueries({ queryKey: ['verifiers'] }); setEditMap({}) },
    onError: (e: any) => toast.error(e.response?.data?.error ?? 'Update failed'),
  })

  return (
    <div className="bg-white rounded-xl border overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-gray-50 text-xs text-gray-500 uppercase">
          <tr>{['Username', 'Name', 'Assignments', 'Team Lead', 'Assigned Room', ''].map(h =>
            <th key={h} className="px-4 py-3 text-left">{h}</th>
          )}</tr>
        </thead>
        <tbody className="divide-y">
          {verifiers.length === 0
            ? <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">No verifiers yet</td></tr>
            : verifiers.map(v => (
              <tr key={v.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-mono text-xs">{v.username}</td>
                <td className="px-4 py-3">{v.name}</td>
                <td className="px-4 py-3 text-xs text-gray-500">
                  {v.assignments.map(a => `${a.day === 'day1' ? 'D1' : 'D2'}: ${a.roomName}`).join(', ')}
                  {v.isTeamLead && v.assignedRoomName && (
                    <span className="ml-1 px-2 py-0.5 text-xs rounded-full bg-purple-100 text-purple-700 font-medium">
                      TL: {v.assignedRoomName}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3">
                  <input type="checkbox" checked={getEdit(v).isTeamLead}
                    onChange={e => setEditMap(m => ({ ...m, [v.id]: { ...getEdit(v), isTeamLead: e.target.checked, assignedRoomId: e.target.checked ? getEdit(v).assignedRoomId : null } }))}
                    className="w-4 h-4 accent-indigo-600 cursor-pointer" />
                </td>
                <td className="px-4 py-3">
                  {getEdit(v).isTeamLead ? (
                    <select value={getEdit(v).assignedRoomId ?? ''}
                      onChange={e => setEditMap(m => ({ ...m, [v.id]: { ...getEdit(v), assignedRoomId: e.target.value ? Number(e.target.value) : null } }))}
                      className="border rounded-lg px-2 py-1 text-xs">
                      <option value="">No Room</option>
                      {allRooms.map(r => <option key={r.id} value={r.id}>{r.roomName} ({r.day === 'day1' ? 'D1' : 'D2'})</option>)}
                    </select>
                  ) : <span className="text-xs text-gray-400">—</span>}
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-1">
                    {editMap[v.id] && (
                      <button onClick={() => updateTL.mutate({ id: v.id, payload: editMap[v.id] })}
                        disabled={updateTL.isPending}
                        className="px-3 py-1 text-xs bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-60">
                        Save
                      </button>
                    )}
                    <button onClick={() => { if (confirm(`Demote ${v.name}?`)) demote.mutate(v.id) }}
                      className="flex items-center gap-1 px-3 py-1 text-xs bg-red-100 text-red-600 rounded-lg hover:bg-red-200">
                      <UserX size={12} /> Demote
                    </button>
                  </div>
                </td>
              </tr>
            ))
          }
        </tbody>
      </table>
    </div>
  )
}
