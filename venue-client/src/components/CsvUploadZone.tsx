import { useCallback, useState } from 'react'
import { useDropzone } from 'react-dropzone'
import { Upload } from 'lucide-react'
import toast from 'react-hot-toast'

interface Props {
  label: string
  onUpload: (file: File) => Promise<void>
}

export default function CsvUploadZone({ label, onUpload }: Props) {
  const [loading, setLoading] = useState(false)

  const onDrop = useCallback(async (files: File[]) => {
    const file = files[0]
    if (!file) return
    if (!file.name.endsWith('.csv')) { toast.error('Only .csv files allowed'); return }
    if (file.size > 10 * 1024 * 1024) { toast.error('File too large (max 10MB)'); return }
    setLoading(true)
    try {
      await onUpload(file)
    } catch {
      toast.error('Upload failed')
    } finally {
      setLoading(false)
    }
  }, [onUpload])

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { 'text/csv': ['.csv'] },
    multiple: false,
  })

  return (
    <div {...getRootProps()} className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${isDragActive ? 'border-indigo-400 bg-indigo-50' : 'border-gray-300 hover:border-indigo-300'}`}>
      <input {...getInputProps()} />
      <Upload size={24} className="mx-auto mb-2 text-gray-400" />
      <p className="text-sm text-gray-500">
        {loading ? 'Uploading…' : `Drop ${label} CSV here or click to browse`}
      </p>
    </div>
  )
}
