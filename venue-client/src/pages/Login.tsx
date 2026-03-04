import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login } from '@/api/auth'
import { useAuthStore } from '@/store/authStore'
import toast from 'react-hot-toast'

export default function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const setAuth = useAuthStore((s) => s.setAuth)
  const navigate = useNavigate()

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      const { data } = await login(username, password)
      setAuth(data.token, data.role, data.username)
      navigate('/')
    } catch (err: any) {
      const status = err.response?.status
      if (status === 429) toast.error(err.response.data.error)
      else toast.error('Invalid credentials')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <form onSubmit={submit}
        className="bg-white rounded-xl shadow p-8 w-full max-w-sm space-y-5">
        <h2 className="text-2xl font-bold text-gray-800">Venue Admin</h2>

        <div className="space-y-3">
          <input value={username} onChange={e => setUsername(e.target.value)}
            placeholder="Username" required
            className="w-full border rounded-lg px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-indigo-500" />
          <input value={password} onChange={e => setPassword(e.target.value)}
            placeholder="Password" type="password" required
            className="w-full border rounded-lg px-4 py-2.5 text-sm outline-none focus:ring-2 focus:ring-indigo-500" />
        </div>

        <button type="submit" disabled={loading}
          className="w-full bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg py-2.5 text-sm font-medium transition-colors disabled:opacity-60">
          {loading ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  )
}
