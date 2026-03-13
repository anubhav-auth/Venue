import { useState, useEffect, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getDashboardStats, closeDay } from "@/api/dashboard";
import { useNavigate } from "react-router-dom";
import { Client } from "@stomp/stompjs";
import toast from "react-hot-toast";

// New imports for the Reports section
import { Users, DoorOpen, UserX, Activity } from "lucide-react";
import {
  downloadFullAttendance,
  downloadRoomSummary,
  downloadNotCheckedIn,
  downloadVerifierActivity,
} from "@/api/reports";
import { triggerFileDownload } from "@/utils/downloadFile";

export default function Dashboard() {
  const [day, setDay] = useState<"day1" | "day2">("day1");
  const qc = useQueryClient();
  const navigate = useNavigate();
  const stompRef = useRef<Client | null>(null);

  // Export loading state
  const [isExporting, setIsExporting] = useState<string | null>(null);

  const { data: stats, isError } = useQuery({
    queryKey: ["dashboard", day],
    queryFn: () => getDashboardStats(day).then((r) => r.data),
    refetchInterval: 30_000,
    retry: 1,
  });

  useEffect(() => {
    let client: Client | null = null;
    try {
      client = new Client({
        brokerURL: `ws://${window.location.host}/ws/websocket`,
        reconnectDelay: 5000,
        onConnect: () => {
          client?.subscribe(`/topic/checkin/${day}`, () => {
            qc.invalidateQueries({ queryKey: ["dashboard", day] });
          });
        },
        onStompError: (frame) => console.warn("STOMP error", frame),
        onDisconnect: () => console.log("STOMP disconnected"),
        onWebSocketError: (e) => console.warn("WS error", e),
      });
      client.activate();
      stompRef.current = client;
    } catch (e) {
      console.warn("STOMP init failed", e);
    }
    return () => {
      client?.deactivate();
    };
  }, [day, qc]);

  const close = useMutation({
    mutationFn: () => closeDay(day),
    onSuccess: (r) => {
      toast.success(
        `Day closed. ${r.data.autodemotedCount} verifiers demoted.`,
      );
      qc.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: () => toast.error("Failed to close day"),
  });

  // Report Export Handler
  const handleExport = async (
    type: string,
    apiCall: () => Promise<any>,
    filename: string,
  ) => {
    setIsExporting(type);
    const toastId = toast.loading(`Generating ${filename}...`);
    try {
      const response = await apiCall();
      triggerFileDownload(response.data, filename);
      toast.success("Download complete!", { id: toastId });
    } catch (error) {
      console.error(error);
      toast.error("Failed to generate report", { id: toastId });
    } finally {
      setIsExporting(null);
    }
  };

  const statusColor = (s: string) =>
    s === "high"
      ? "bg-green-100 text-green-700"
      : s === "medium"
        ? "bg-yellow-100 text-yellow-700"
        : "bg-red-100 text-red-700";

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <h1 className="text-xl font-semibold text-gray-800">Dashboard</h1>
        <button
          onClick={() => {
            if (
              window.confirm(
                `Close ${day}? This will auto-demote single-day verifiers.`,
              )
            )
              close.mutate();
          }}
          disabled={close.isPending}
          className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-60"
        >
          {close.isPending
            ? "Closing…"
            : `Close ${day === "day1" ? "Day 1" : "Day 2"}`}
        </button>
      </div>

      {/* Day tabs */}
      <div className="flex gap-2">
        {(["day1", "day2"] as const).map((d) => (
          <button
            key={d}
            onClick={() => setDay(d)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
              day === d
                ? "bg-indigo-600 text-white"
                : "bg-gray-100 text-gray-600 hover:bg-gray-200"
            }`}
          >
            {d === "day1" ? "Day 1" : "Day 2"}
          </button>
        ))}
      </div>

      {/* Error state */}
      {isError && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-sm text-red-600">
          Failed to load stats. Backend may be unavailable — retrying every 30s.
        </div>
      )}

      {/* Overall stats cards */}
      {stats ? (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[
            { label: "Total Students", value: stats.overall.totalStudents },
            {
              label: "Checked In",
              value: `${stats.overall.checkedIn} (${(stats.overall.percentage ?? 0).toFixed(1)}%)`,
            },
            { label: "Not Checked In", value: stats.overall.notCheckedIn },
          ].map(({ label, value }) => (
            <div key={label} className="bg-white rounded-xl border p-5">
              <p className="text-xs text-gray-500 mb-1">{label}</p>
              <p className="text-2xl font-bold text-gray-800">{value}</p>
            </div>
          ))}
        </div>
      ) : (
        !isError && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                className="bg-white rounded-xl border p-5 animate-pulse"
              >
                <div className="h-3 bg-gray-200 rounded w-24 mb-2" />
                <div className="h-8 bg-gray-200 rounded w-16" />
              </div>
            ))}
          </div>
        )
      )}

      {/* NEW: Event Reports & Exports Section */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mt-6">
        <h2 className="text-lg font-bold text-gray-900 mb-4">
          Event Reports & Exports
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <button
            onClick={() =>
              handleExport(
                "attendance",
                downloadFullAttendance,
                "full_attendance.csv",
              )
            }
            disabled={isExporting !== null}
            className="flex items-center gap-3 p-4 rounded-lg border border-gray-200 hover:border-indigo-500 hover:bg-indigo-50 transition-all text-left group disabled:opacity-50"
          >
            <div className="bg-indigo-100 p-2 rounded-md text-indigo-600 group-hover:bg-indigo-600 group-hover:text-white transition-colors">
              <Users size={20} />
            </div>
            <div>
              <p className="font-semibold text-gray-900 text-sm">
                Full Attendance
              </p>
              <p className="text-xs text-gray-500">Master check-in list</p>
            </div>
          </button>

          <button
            onClick={() =>
              handleExport("summary", downloadRoomSummary, "room_summary.csv")
            }
            disabled={isExporting !== null}
            className="flex items-center gap-3 p-4 rounded-lg border border-gray-200 hover:border-indigo-500 hover:bg-indigo-50 transition-all text-left group disabled:opacity-50"
          >
            <div className="bg-emerald-100 p-2 rounded-md text-emerald-600 group-hover:bg-emerald-600 group-hover:text-white transition-colors">
              <DoorOpen size={20} />
            </div>
            <div>
              <p className="font-semibold text-gray-900 text-sm">
                Room Summary
              </p>
              <p className="text-xs text-gray-500">Capacity vs Occupancy</p>
            </div>
          </button>

          <button
            onClick={() =>
              handleExport("absent", downloadNotCheckedIn, "not_checked_in.csv")
            }
            disabled={isExporting !== null}
            className="flex items-center gap-3 p-4 rounded-lg border border-gray-200 hover:border-indigo-500 hover:bg-indigo-50 transition-all text-left group disabled:opacity-50"
          >
            <div className="bg-rose-100 p-2 rounded-md text-rose-600 group-hover:bg-rose-600 group-hover:text-white transition-colors">
              <UserX size={20} />
            </div>
            <div>
              <p className="font-semibold text-gray-900 text-sm">
                Not Checked In
              </p>
              <p className="text-xs text-gray-500">Absentee list</p>
            </div>
          </button>

          <button
            onClick={() =>
              handleExport(
                "verifier",
                downloadVerifierActivity,
                "verifier_activity.csv",
              )
            }
            disabled={isExporting !== null}
            className="flex items-center gap-3 p-4 rounded-lg border border-gray-200 hover:border-indigo-500 hover:bg-indigo-50 transition-all text-left group disabled:opacity-50"
          >
            <div className="bg-amber-100 p-2 rounded-md text-amber-600 group-hover:bg-amber-600 group-hover:text-white transition-colors">
              <Activity size={20} />
            </div>
            <div>
              <p className="font-semibold text-gray-900 text-sm">
                Verifier Activity
              </p>
              <p className="text-xs text-gray-500">Scan metrics by user</p>
            </div>
          </button>
        </div>
      </div>

      {/* Room table */}
      <div className="bg-white rounded-xl border overflow-hidden">
        <div className="overflow-x-auto">
        <table className="w-full text-sm min-w-[600px]">
          <thead className="bg-gray-50 text-xs text-gray-500 uppercase">
            <tr>
              {["Room", "Capacity", "Checked In", "%", "Status", ""].map(
                (h) => (
                  <th key={h} className="px-4 py-3 text-left font-medium">
                    {h}
                  </th>
                ),
              )}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {(stats?.rooms ?? []).length === 0 ? (
              <tr>
                <td
                  colSpan={6}
                  className="px-4 py-10 text-center text-gray-400 text-sm"
                >
                  {stats ? "No rooms found for this day" : "Loading…"}
                </td>
              </tr>
            ) : (
              (stats?.rooms ?? []).map((r) => (
                <tr
                  key={r.roomId}
                  className="hover:bg-gray-50 transition-colors"
                >
                  <td className="px-4 py-3 font-medium text-gray-800">
                    {r.roomName}
                  </td>
                  <td className="px-4 py-3 text-gray-500">{r.capacity}</td>
                  <td className="px-4 py-3 text-gray-700">{r.checkedIn}</td>
                  <td className="px-4 py-3 text-gray-700">
                    {(r.percentage ?? 0).toFixed(1)}%
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`px-2 py-0.5 text-xs rounded-full font-medium ${statusColor(r.status)}`}
                    >
                      {r.status}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() =>
                        navigate(`/rooms/${r.roomId}/detail?day=${day}`)
                      }
                      className="text-xs text-indigo-600 hover:underline font-medium"
                    >
                      View →
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        </div>
      </div>

      {stats?.lastUpdated && (
        <p className="text-xs text-gray-400 text-right">
          Last updated: {new Date(stats.lastUpdated).toLocaleTimeString()}
        </p>
      )}
    </div>
  );
}
