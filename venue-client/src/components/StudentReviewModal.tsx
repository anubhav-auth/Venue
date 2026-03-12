import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { addReview } from '@/api/reviews'
import toast from 'react-hot-toast'
import { X, MessageSquarePlus } from 'lucide-react'

interface Props {
  studentId: number
  studentName: string
  regNo: string
  checkInId: number
  day: string
  onClose: () => void
  existingReviews?: Array<{
    id: number
    reviewText: string
    addedByUsername: string
    addedAt: string
  }>
}

export default function StudentReviewModal({
  studentId,
  studentName,
  regNo,
  checkInId,
  day,
  onClose,
  existingReviews = [],
}: Props) {
  const [text, setText] = useState('')

  const mutation = useMutation({
    mutationFn: () => addReview(checkInId, studentId, day, text),
    onSuccess: () => {
      toast.success('Review added')
      setText('')
      onClose()
    },
    onError: () => toast.error('Failed to add review'),
  })

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md p-6 space-y-4">
        <div className="flex items-start justify-between">
          <div>
            <h2 className="font-semibold text-gray-800">{studentName}</h2>
            <p className="text-xs text-gray-400">{regNo} · {day}</p>
          </div>
          <button
            onClick={onClose}
            className="p-1 text-gray-400 hover:text-gray-600"
          >
            <X size={18} />
          </button>
        </div>

        {/* Existing reviews */}
        {existingReviews.length > 0 && (
          <div className="space-y-2 max-h-36 overflow-y-auto">
            {existingReviews.map((r) => (
              <div key={r.id} className="bg-gray-50 rounded-lg p-3">
                <p className="text-sm text-gray-700">{r.reviewText}</p>
                <p className="text-xs text-gray-400 mt-1">
                  — {r.addedByUsername} · {new Date(r.addedAt).toLocaleString()}
                </p>
              </div>
            ))}
          </div>
        )}

        {/* Add review */}
        <div className="space-y-3">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Add a note or review…"
            rows={3}
            className="w-full border rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-indigo-300"
          />
          <div className="flex justify-end gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 text-sm border rounded-lg hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              onClick={() => mutation.mutate()}
              disabled={!text.trim() || mutation.isPending}
              className="flex items-center gap-2 px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50"
            >
              <MessageSquarePlus size={14} />
              {mutation.isPending ? 'Saving…' : 'Add Review'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
