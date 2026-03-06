// src/pages/Students.tsx
import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { uploadAudienceCsv, uploadVolunteerCsv, getVolunteers, adminScanVolunteer, promoteVolunteer, getVerifiers, demoteVerifier, pollAudienceUploadStatus, pollVolunteerUploadStatus } from '@/api/volunteers'
import { getRooms } from '@/api/rooms'
import CsvUploadZone from '@/components/CsvUploadZone'
import toast from 'react-hot-toast'
import { Html5Qrcode } from 'html5-qrcode'
import { Camera, CameraOff, UserCheck, UserX } from 'lucide-react'
import { useCsvUpload } from '../hooks/useCsvUpload'

type Tab = 'import' | 'volunteers' | 'verifiers'

export default function Students() {
  const [tab, setTab] = useState<Tab>('import')
  const qc = useQueryClient()

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold">People</h1>
      <div className="flex gap-2">
        {(['import', 'volunteers', 'verifiers'] as Tab[]).map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium capitalize ${tab === t ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
            {t}
          </button>
        ))}
      </div>

      {tab === 'import' && <ImportTab qc={qc}/>}
      {tab === 'volunteers' && <VolunteersTab qc={qc} />}
      {tab === 'verifiers' && <VerifiersTab qc={qc} />}
    </div>
  )
}

// REPLACE the entire function ImportTab:
function ImportTab({ qc }: { qc: ReturnType<typeof useQueryClient> }) {
  const audience = useCsvUpload(uploadAudienceCsv, pollAudienceUploadStatus, ['students'])
  const volunteers = useCsvUpload(uploadVolunteerCsv, pollVolunteerUploadStatus, ['students'])

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div className="bg-white rounded-xl border p-6 space-y-3">
        <h2 className="font-semibold text-sm">Audience CSV Upload</h2>
        <p className="text-xs text-gray-400">Required columns: Name, Regdno, Emailid, Degree, Contactno, Passoutyear</p>
        <CsvUploadZone label="Audience" onUpload={audience.upload} />
        {audience.status === 'processing' && (
          <p className="text-xs text-indigo-500 animate-pulse">⏳ Processing in background…</p>
        )}
        {audience.status === 'done' && <p className="text-xs text-green-600">{audience.msg}</p>}
        {audience.status === 'error' && <p className="text-xs text-red-500">{audience.msg}</p>}
      </div>

      <div className="bg-white rounded-xl border p-6 space-y-3">
        <h2 className="font-semibold text-sm">Volunteer CSV Upload</h2>
        <p className="text-xs text-gray-400">Same columns as audience. QR generated automatically on import.</p>
        <CsvUploadZone label="Volunteers" onUpload={volunteers.upload} />
        {volunteers.status === 'processing' && (
          <p className="text-xs text-indigo-500 animate-pulse">⏳ Processing in background…</p>
        )}
        {volunteers.status === 'done' && <p className="text-xs text-green-600">{volunteers.msg}</p>}
        {volunteers.status === 'error' && <p className="text-xs text-red-500">{volunteers.msg}</p>}
      </div>
    </div>
  )
}


function VolunteersTab({ qc }: { qc: ReturnType<typeof useQueryClient> }) {
  const [promoteModal, setPromoteModal] = useState<{ open: boolean; id?: number; name?: string }>({ open: false })
  const [scanActive, setScanActive] = useState(false)
  const scannerRef = useRef<Html5Qrcode | null>(null)

  const { data: volunteers = [] } = useQuery({ queryKey: ['volunteers'], queryFn: () => getVolunteers().then(r => r.data) })
  const { data: day1Rooms = [] } = useQuery({ queryKey: ['rooms', 'day1'], queryFn: () => getRooms('day1').then(r => r.data) })
  const { data: day2Rooms = [] } = useQuery({ queryKey: ['rooms', 'day2'], queryFn: () => getRooms('day2').then(r => r.data) })

  const [assignments, setAssignments] = useState<{ day: string; roomId: number }[]>([{ day: 'day1', roomId: 0 }])

  const scan = useMutation({
    mutationFn: (qrData: string) => adminScanVolunteer(qrData),
    onSuccess: (r) => { toast.success(`✅ Scanned: ${r.data.name}`); qc.invalidateQueries({ queryKey: ['volunteers'] }) },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Scan failed')
  })

  const promote = useMutation({
    mutationFn: ({ id, a }: { id: number; a: typeof assignments }) => promoteVolunteer(id, a),
    onSuccess: () => {
      toast.success('Promoted to verifier!')
      qc.invalidateQueries({ queryKey: ['volunteers'] })
      qc.invalidateQueries({ queryKey: ['verifiers'] })
      setPromoteModal({ open: false })
    },
    onError: (e: any) => toast.error(e.response?.data?.error || 'Promotion failed')
  })

  const startScan = async () => {
    setScanActive(true)
    const scanner = new Html5Qrcode('admin-qr-reader')
    scannerRef.current = scanner
    await scanner.start({ facingMode: 'environment' }, { fps: 10, qrbox: 250 },
      (text) => { scan.mutate(text); stopScan() }, () => {})
  }
  const stopScan = async () => {
    setScanActive(false)
    try { await scannerRef.current?.stop() } catch {}
  }

  return (
    <div className="space-y-6">
      {/* Admin QR Scanner */}
      <div className="bg-white rounded-xl border p-6 space-y-3">
        <h2 className="font-semibold text-sm">Scan Volunteer QR (Admin Bootstrap)</h2>
        <div id="admin-qr-reader" className={scanActive ? 'w-full max-w-xs' : 'hidden'} />
        <button onClick={scanActive ? stopScan : startScan}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium ${scanActive ? 'bg-red-100 text-red-600' : 'bg-indigo-600 text-white hover:bg-indigo-700'}`}>
          {scanActive ? <><CameraOff size={14} /> Stop Scanner</> : <><Camera size={14} /> Open Scanner</>}
        </button>
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
            {volunteers.map(v => (
              <tr key={v.studentId} className="hover:bg-gray-50">  {/* ← studentId */}
                <td className="px-4 py-3 font-mono text-xs">{v.regNo}</td>
                <td className="px-4 py-3">{v.name}</td>
                <td className="px-4 py-3 text-xs text-gray-400">{v.email}</td>
                <td className="px-4 py-3">{v.day1Attended ? '✅' : '—'}</td>
                <td className="px-4 py-3">{v.day2Attended ? '✅' : '—'}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${v.isPromoted ? 'bg-purple-100 text-purple-700' : 'bg-gray-100 text-gray-600'}`}>
                    {v.isPromoted ? 'Promoted' : 'Volunteer'}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <button
                    disabled={!v.canPromote || v.isPromoted}
                    onClick={() => {
                      setAssignments([{ day: 'day1', roomId: 0 }])
                      setPromoteModal({ open: true, id: v.studentId, name: v.name })  // ← studentId
                    }}
                    className="flex items-center gap-1 px-3 py-1 text-xs bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed">
                    <UserCheck size={12} /> {v.isPromoted ? 'Promoted' : 'Promote'}
                  </button>
                </td>
              </tr>
            ))}
            {volunteers.length === 0 && (
              <tr><td colSpan={7} className="px-4 py-8 text-center text-gray-400">No volunteers imported yet</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Promote modal */}
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


function VerifiersTab({ qc }: { qc: ReturnType<typeof useQueryClient> }) {
  const { data: verifiers = [] } = useQuery({ queryKey: ['verifiers'], queryFn: () => getVerifiers().then(r => r.data) })

  const demote = useMutation({
    mutationFn: (id: number) => demoteVerifier(id),
    onSuccess: () => { toast.success('Demoted'); qc.invalidateQueries({ queryKey: ['verifiers'] }); qc.invalidateQueries({ queryKey: ['volunteers'] }) }
  })

  return (
    <div className="bg-white rounded-xl border overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-gray-50 text-xs text-gray-500 uppercase">
          <tr>{['Username', 'Name', 'Assignments', ''].map(h => <th key={h} className="px-4 py-3 text-left">{h}</th>)}</tr>
        </thead>
        <tbody className="divide-y">
          {verifiers.map(v => (
            <tr key={v.id} className="hover:bg-gray-50">
              <td className="px-4 py-3 font-mono text-xs">{v.username}</td>
              <td className="px-4 py-3">{v.name}</td>
              <td className="px-4 py-3 text-xs text-gray-500">{v.assignments.map(a => `${a.day === 'day1' ? 'D1' : 'D2'}: ${a.roomName}`).join(', ')}</td>
              <td className="px-4 py-3">
                <button onClick={() => { if (confirm(`Demote ${v.name}?`)) demote.mutate(v.id) }}
                  className="flex items-center gap-1 px-3 py-1 text-xs bg-red-100 text-red-600 rounded-lg hover:bg-red-200">
                  <UserX size={12} /> Demote
                </button>
              </td>
            </tr>
          ))}
          {verifiers.length === 0 && <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">No verifiers yet</td></tr>}
        </tbody>
      </table>
    </div>
  )
}
