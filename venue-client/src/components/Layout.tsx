import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { Building2, Users, LayoutGrid, QrCode, LogOut, LayoutDashboard } from 'lucide-react'
import clsx from 'clsx'


const nav = [
  { to: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: 'rooms',     label: 'Rooms',     icon: Building2 },
  { to: 'students',  label: 'People',    icon: Users },
  { to: 'allocations', label: 'Allocations', icon: LayoutGrid },
  { to: 'checkin',   label: 'Check-in',  icon: QrCode },
]

export default function Layout() {
  const { username, logout } = useAuthStore()
  const navigate = useNavigate()

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside className="w-56 bg-white border-r flex flex-col">
        <div className="px-6 py-5 border-b">
          <h1 className="text-lg font-bold text-gray-800">Venue Admin</h1>
          <p className="text-xs text-gray-400 mt-0.5">{username}</p>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1">
          {nav.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to}
              className={({ isActive }) => clsx(
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-indigo-50 text-indigo-700'
                  : 'text-gray-600 hover:bg-gray-100'
              )}>
              <Icon size={16} />
              {label}
            </NavLink>
          ))}
        </nav>

        <button
          onClick={() => { logout(); navigate('/login') }}
          className="flex items-center gap-3 px-6 py-4 text-sm text-gray-500 hover:text-red-600 border-t transition-colors">
          <LogOut size={16} /> Sign out
        </button>
      </aside>

      {/* Content */}
      <main className="flex-1 overflow-y-auto p-8">
        <Outlet />
      </main>
    </div>
  )
}
