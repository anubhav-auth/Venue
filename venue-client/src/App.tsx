import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'
import { useAuthStore } from '@/store/authStore'
import Layout from '@/components/Layout'
import Login from '@/pages/Login'
import Rooms from '@/pages/Rooms'
import Students from '@/pages/Students'
import Allocations from '@/pages/Allocations'
import CheckIn from '@/pages/CheckIn'

const qc = new QueryClient()

function Guard({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token)
  return token ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <QueryClientProvider client={qc}>
      <Toaster position="top-right" />
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Guard><Layout /></Guard>}>
            <Route index element={<Navigate to="/rooms" replace />} />
            <Route path="rooms"       element={<Rooms />} />
            <Route path="students"    element={<Students />} />
            <Route path="allocations" element={<Allocations />} />
            <Route path="checkin"     element={<CheckIn />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
