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
  isTeamLead: boolean
  assignedRoomId: number | null
  assignments: VerifierAssignment[] | null

  setAuth: (
    token: string,
    role: string,
    username: string,
    name?: string | null,
    isTeamLead?: boolean,
    assignedRoomId?: number | null,
    assignments?: VerifierAssignment[] | null
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
      isTeamLead: false,
      assignedRoomId: null,
      assignments: null,

      setAuth: (
        token,
        role,
        username,
        name = null,
        isTeamLead = false,
        assignedRoomId = null,
        assignments = null
      ) => set({ token, role, username, name, isTeamLead, assignedRoomId, assignments }),

      logout: () =>
        set({
          token: null,
          role: null,
          username: null,
          name: null,
          isTeamLead: false,
          assignedRoomId: null,
          assignments: null,
        }),
    }),
    { name: import.meta.env.VITE_AUTH_STORE_KEY || 'venue-auth' }
  )
)
