import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "react-hot-toast";
import { Component, type ReactNode } from "react";
import { useAuthStore } from "@/store/authStore";
import Layout from "@/components/Layout";
import Login from "@/pages/Login";
import Rooms from "@/pages/Rooms";
import Students from "@/pages/Students";
import Allocations from "@/pages/Allocations";
import CheckIn from "@/pages/CheckIn";
import Dashboard from "@/pages/Dashboard";
import RoomDetail from "@/pages/RoomDetail";
import StudentDashboard from "@/pages/StudentDashboard"; // Added import for the Student Portal
import VerifierLayout     from './layouts/VerifierLayout'
import VerifierScan       from './pages/verifier/VerifierScan'
import VerifierRoom       from './pages/verifier/VerifierRoom'
import VerifierVolunteers from './pages/verifier/VerifierVolunteers'
import VolunteerDashboard from "./pages/VolunteerDashboard";

// ── Error Boundary ──────────────────────────────────────────────────────────
class ErrorBoundary extends Component<
  { children: ReactNode },
  { error: string | null }
> {
  state = { error: null };

  static getDerivedStateFromError(e: Error) {
    return { error: e.message };
  }

  render() {
    if (this.state.error) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50 p-8">
          <div className="bg-white rounded-xl border border-red-200 p-8 max-w-lg w-full shadow-sm">
            <p className="text-red-600 font-semibold text-sm mb-2">
              Something went wrong
            </p>
            <p className="font-mono text-xs text-gray-600 bg-gray-50 p-3 rounded-lg mb-4 break-all">
              {this.state.error}
            </p>
            <button
              onClick={() => this.setState({ error: null })}
              className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700"
            >
              Try again
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}

const qc = new QueryClient({
  defaultOptions: {
    queries: {
      retry:     Number(import.meta.env.VITE_QUERY_RETRY)      || 1,
      staleTime: Number(import.meta.env.VITE_QUERY_STALE_TIME) || 10_000,
    },
  },
})


// Protects Admin/Verifier Routes (Layout + Sidebar)
function AdminGuard({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const role = useAuthStore((s) => s.role);

  if (!token) return <Navigate to="/login" replace />;
  if (role === "AUDIENCE") return <Navigate to="/my-seat" replace />;
  if (role === "VOLUNTEER") return <Navigate to="/my-pass" replace />;
  if (role === "VERIFIER") return <Navigate to="/verifier" replace />;
  return <>{children}</>;
}

// Protects the Student Portal
function StudentGuard({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const role = useAuthStore((s) => s.role);
  if (!token) return <Navigate to="/login" replace />;
  if (role !== "AUDIENCE" && role !== "VOLUNTEER")
    return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}

// Smart fallback to route users to the right place if they hit a 404
function FallbackRedirect() {
  const token = useAuthStore((s) => s.token);
  const role = useAuthStore((s) => s.role);
  if (!token) return <Navigate to="/login" replace />;
  if (role === "AUDIENCE") return <Navigate to="/my-seat" replace />;
  if (role === "VOLUNTEER") return <Navigate to="/my-pass" replace />;
  if (role === "VERIFIER") return <Navigate to="/verifier" replace />;
  return <Navigate to="/dashboard" replace />;
}

function VerifierGuard({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const role = useAuthStore((s) => s.role);
  if (!token) return <Navigate to="/login" replace />;
  if (role !== "VERIFIER") return <Navigate to="/login" replace />;
  return <>{children}</>;
}

// ── App ──────────────────────────────────────────────────────────────────────
export default function App() {
  return (
    <QueryClientProvider client={qc}>
      <Toaster position="top-right" toastOptions={{ duration: Number(import.meta.env.VITE_TOAST_DURATION) || 3000 }} />
      <BrowserRouter>
        <ErrorBoundary>
          <Routes>
            <Route path="/login" element={<Login />} />

            {/* Student (Audience) Portal */}
            <Route
              path="/my-seat"
              element={
                <StudentGuard>
                  <StudentDashboard />
                </StudentGuard>
              }
            />

            {/* Admin & Verifier Dashboard */}
            <Route
              path="/"
              element={
                <AdminGuard>
                  <Layout />
                </AdminGuard>
              }
            >
              <Route index element={<Navigate to="/dashboard" replace />} />
              <Route
                path="dashboard"
                element={
                  <ErrorBoundary>
                    <Dashboard />
                  </ErrorBoundary>
                }
              />
              <Route
                path="rooms"
                element={
                  <ErrorBoundary>
                    <Rooms />
                  </ErrorBoundary>
                }
              />
              <Route
                path="rooms/:id/detail"
                element={
                  <ErrorBoundary>
                    <RoomDetail />
                  </ErrorBoundary>
                }
              />
              <Route
                path="students"
                element={
                  <ErrorBoundary>
                    <Students />
                  </ErrorBoundary>
                }
              />
              <Route
                path="allocations"
                element={
                  <ErrorBoundary>
                    <Allocations />
                  </ErrorBoundary>
                }
              />
              <Route
                path="checkin"
                element={
                  <ErrorBoundary>
                    <CheckIn />
                  </ErrorBoundary>
                }
              />
            </Route>

            <Route
              path="/my-pass"
              element={
                <StudentGuard>
                  <VolunteerDashboard />
                </StudentGuard>
              }
            />
            <Route
              path="/verifier"
              element={
                <VerifierGuard><VerifierLayout /></VerifierGuard>
              }
            >
              <Route index element={<Navigate to="scan" replace />} />
              <Route path="scan"       element={<VerifierScan />} />
              <Route path="room"       element={<VerifierRoom />} />
              <Route path="volunteers" element={<VerifierVolunteers />} />
            </Route>

            {/* Fallback Router */}
            <Route path="*" element={<FallbackRedirect />} />
          </Routes>
        </ErrorBoundary>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
