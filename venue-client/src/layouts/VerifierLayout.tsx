import { NavLink, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { QrCode, LayoutDashboard, Users } from 'lucide-react'

export default function VerifierLayout() {
  const { name, isTeamLead, logout } = useAuthStore()

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b sticky top-0 z-10">
        <div className="max-w-2xl mx-auto px-4 py-3 flex items-center justify-between">
          <div>
            <h1 className="font-semibold text-gray-800">
              {isTeamLead ? '⭐ Team Lead' : 'Verifier Portal'}
            </h1>
            <p className="text-xs text-gray-400">{name}</p>
          </div>
          <button
            onClick={logout}
            className="text-sm text-red-500 hover:text-red-700 font-medium"
          >
            Logout
          </button>
        </div>
      </header>

      {/* Content */}
      <div className="max-w-2xl mx-auto px-4 py-4">
        <Outlet />
      </div>

      {/* Bottom Nav */}
      <nav className="fixed bottom-0 left-0 right-0 bg-white border-t flex">
        <NavLink
          to="/verifier/scan"
          className={({ isActive }) =>
            `flex-1 flex flex-col items-center gap-1 py-2.5 text-xs font-medium transition-colors ${
              isActive ? 'text-indigo-600' : 'text-gray-400 hover:text-gray-600'
            }`
          }
        >
          <QrCode size={20} />
          Scan
        </NavLink>

        {isTeamLead && (
          <>
            <NavLink
              to="/verifier/room"
              className={({ isActive }) =>
                `flex-1 flex flex-col items-center gap-1 py-2.5 text-xs font-medium transition-colors ${
                  isActive ? 'text-indigo-600' : 'text-gray-400 hover:text-gray-600'
                }`
              }
            >
              <LayoutDashboard size={20} />
              My Room
            </NavLink>
            <NavLink
              to="/verifier/volunteers"
              className={({ isActive }) =>
                `flex-1 flex flex-col items-center gap-1 py-2.5 text-xs font-medium transition-colors ${
                  isActive ? 'text-indigo-600' : 'text-gray-400 hover:text-gray-600'
                }`
              }
            >
              <Users size={20} />
              Volunteers
            </NavLink>
          </>
        )}
      </nav>

      {/* Bottom padding so content doesn't hide behind nav */}
      <div className="h-20" />
    </div>
  )
}
