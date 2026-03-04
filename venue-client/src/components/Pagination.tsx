// src/components/Pagination.tsx
import { ChevronLeft, ChevronRight } from 'lucide-react'

interface Props {
  page: number
  totalPages: number
  onPageChange: (p: number) => void
}

export default function Pagination({ page, totalPages, onPageChange }: Props) {
  if (totalPages <= 1) return null
  return (
    <div className="flex items-center gap-2 justify-end mt-4">
      <button
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
        className="p-1 rounded hover:bg-gray-100 disabled:opacity-40"
      ><ChevronLeft size={16} /></button>
      <span className="text-sm text-gray-600">{page + 1} / {totalPages}</span>
      <button
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        className="p-1 rounded hover:bg-gray-100 disabled:opacity-40"
      ><ChevronRight size={16} /></button>
    </div>
  )
}
