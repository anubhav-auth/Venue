import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface VerifierAssignment {
  day: string
  roomId: number
  roomName: string
}

interface AuthState {
  token: string | null
  role: string | null
  username: string | null
  name: string | null
  assignments: VerifierAssignment[] | null

  setAuth: (
    token: string,
    role: string,
    username: string,
    name?: string | null,           // ← was `string?`, now `string | null`
    assignments?: VerifierAssignment[] | null  // ← same fix
  ) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      role: null,
      username: null,
      name: null,
      assignments: null,

      setAuth: (token, role, username, name = null, assignments = null) =>
        set({ token, role, username, name, assignments }),

      logout: () =>
        set({ token: null, role: null, username: null, name: null, assignments: null }),
    }),
    { name: import.meta.env.VITE_AUTH_STORE_KEY || 'venue-auth' }
  )
)
