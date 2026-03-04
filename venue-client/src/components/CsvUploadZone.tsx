// src/components/CsvUploadZone.tsx
import { useCallback, useState } from 'react'
import { useDropzone } from 'react-dropzone'
import { Upload, CheckCircle, XCircle } from 'lucide-react'
import toast from 'react-hot-toast'

interface ImportResult {
  imported: number
  skipped: number
  errors: { row: number; reason: string }[]
}

interface Props {
  label: string
  onUpload: (file: File) => Promise<{ data: ImportResult }>
}

export default function CsvUploadZone({ label, onUpload }: Props) {
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [showErrors, setShowErrors] = useState(false)

  const onDrop = useCallback(async (files: File[]) => {
    const file = files[0]
    if (!file) return
    if (!file.name.endsWith('.csv')) { toast.error('Only .csv files allowed'); return }
    if (file.size > 10 * 1024 * 1024) { toast.error('File too large (max 10MB)'); return }
    setLoading(true); setResult(null)
    try {
      const res = await onUpload(file)
      setResult(res.data)
      toast.success(`Imported ${res.data.imported}, skipped ${res.data.skipped}`)
    } catch {
      toast.error('Upload failed')
    } finally {
      setLoading(false)
    }
  }, [onUpload])

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop, accept: { 'text/csv': ['.csv'] }, multiple: false
  })

  return (
    <div className="space-y-3">
      <div {...getRootProps()} className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors
        ${isDragActive ? 'border-indigo-400 bg-indigo-50' : 'border-gray-300 hover:border-indigo-300'}`}>
        <input {...getInputProps()} />
        <Upload size={24} className="mx-auto mb-2 text-gray-400" />
        <p className="text-sm text-gray-500">{loading ? 'Uploading…' : `Drop ${label} CSV here or click to browse`}</p>
      </div>
      {result && (
        <div className="rounded-lg border p-4 space-y-2">
          <div className="flex gap-4 text-sm">
            <span className="flex items-center gap-1 text-green-600"><CheckCircle size={14} />{result.imported} imported</span>
            <span className="text-gray-500">{result.skipped} skipped</span>
            {result.errors.length > 0 && (
              <button onClick={() => setShowErrors(v => !v)} className="text-red-500 flex items-center gap-1">
                <XCircle size={14} />{result.errors.length} errors
              </button>
            )}
          </div>
          {showErrors && (
            <ul className="text-xs text-red-600 space-y-1 max-h-40 overflow-y-auto">
              {result.errors.map((e, i) => <li key={i}>Row {e.row}: {e.reason}</li>)}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
