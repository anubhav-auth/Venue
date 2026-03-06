import { useState, useRef } from 'react'
import toast from 'react-hot-toast'
import { useQueryClient } from '@tanstack/react-query'

interface UploadStatus {
  status: 'PROCESSING' | 'DONE' | 'ERROR'
  result?: { imported: number; skipped: number; errors: number }
  message?: string
}

export function useCsvUpload(
  uploadFn: (file: File) => Promise<{ data: { jobId: string } }>,
  pollFn: (jobId: string) => Promise<{ data: UploadStatus }>,
  invalidateKey: string[]
) {
  const qc = useQueryClient()
  const [status, setStatus] = useState<'idle' | 'uploading' | 'processing' | 'done' | 'error'>('idle')
  const [msg, setMsg] = useState('')
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const upload = async (file: File) => {
    setStatus('uploading')
    setMsg('')
    try {
      const { data } = await uploadFn(file)
      setStatus('processing')

      pollRef.current = setInterval(async () => {
        try {
          const { data: s } = await pollFn(data.jobId)
          if (s.status === 'DONE') {
            clearInterval(pollRef.current!)
            setStatus('done')
            const r = s.result!
            setMsg(`✓ Imported ${r.imported}, skipped ${r.skipped}`)
            qc.invalidateQueries({ queryKey: invalidateKey })
            r.errors > 0
              ? toast.error(`Done — ${r.errors} row errors`)
              : toast.success(`Imported ${r.imported} successfully`)
          } else if (s.status === 'ERROR') {
            clearInterval(pollRef.current!)
            setStatus('error')
            setMsg(s.message ?? 'Upload failed')
            toast.error(s.message ?? 'Upload failed')
          }
        } catch {
          clearInterval(pollRef.current!)
          setStatus('error')
          setMsg('Lost connection while polling')
        }
      }, 2000)
    } catch (e: any) {
      setStatus('error')
      setMsg(e.response?.data?.error ?? 'Upload failed')
      toast.error('Upload failed')
    }
  }

  return { upload, status, msg }
}
