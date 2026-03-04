// src/pages/Dashboard.tsx
import { useState, useEffect, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getDashboardStats, closeDay } from "../api/dashboard";
import { useNavigate } from "react-router-dom";
import { Client } from "@stomp/stompjs";
import toast from "react-hot-toast";
import { useMutation } from "@tanstack/react-query";

export default function Dashboard() {
  const [day, setDay] = useState<"day1" | "day2">("day1");
  const qc = useQueryClient();
  const navigate = useNavigate();
  const stompRef = useRef<Client | null>(null);

  const { data: stats } = useQuery({
    queryKey: ["dashboard", day],
    queryFn: () => getDashboardStats(day).then((r) => r.data),
    refetchInterval: 30_000,
  });

  // Replace the useEffect in Dashboard.tsx with this:
  useEffect(() => {
    let client: Client | null = null;
    try {
      client = new Client({
        brokerURL: `ws://${window.location.host}/ws/websocket`,
        reconnectDelay: 5000,
        onConnect: () => {
          client?.subscribe(`/topic/dashboard/${day}`, () => {
            qc.invalidateQueries({ queryKey: ["dashboard", day] });
          });
        },
        onStompError: (frame) => {
          console.warn("STOMP error", frame); // don't throw — just log
        },
        onDisconnect: () => {
          console.log("STOMP disconnected");
        },
        onWebSocketError: (e) => {
          console.warn("WS error", e); // swallow — stats still refresh on interval
        },
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
  });

  const statusColor = (s: string) =>
    s === "high"
      ? "bg-green-100 text-green-700"
      : s === "medium"
        ? "bg-yellow-100 text-yellow-700"
        : "bg-red-100 text-red-700";

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Dashboard</h1>
        <button
          onClick={() => {
            if (
              confirm(
                `Close ${day}? This will auto-demote single-day verifiers.`,
              )
            )
              close.mutate();
          }}
          className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700"
        >
          Close {day === "day1" ? "Day 1" : "Day 2"}
        </button>
      </div>

      {/* Day tabs */}
      <div className="flex gap-2">
        {(["day1", "day2"] as const).map((d) => (
          <button
            key={d}
            onClick={() => setDay(d)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium ${day === d ? "bg-indigo-600 text-white" : "bg-gray-100 text-gray-600"}`}
          >
            {d === "day1" ? "Day 1" : "Day 2"}
          </button>
        ))}
      </div>

      {/* Overall stats */}
      {stats && (
        <div className="grid grid-cols-3 gap-4">
          {[
            { label: "Total Students", value: stats.totalStudents },
            {
              label: "Checked In",
              value: `${stats.checkedIn} (${stats.percentage.toFixed(1)}%)`,
            },
            { label: "Not Checked In", value: stats.notCheckedIn },
          ].map(({ label, value }) => (
            <div key={label} className="bg-white rounded-xl border p-5">
              <p className="text-xs text-gray-500 mb-1">{label}</p>
              <p className="text-2xl font-bold text-gray-800">{value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Room table */}
      <div className="bg-white rounded-xl border overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-xs text-gray-500 uppercase">
            <tr>
              {["Room", "Capacity", "Checked In", "%", "Status", ""].map(
                (h) => (
                  <th key={h} className="px-4 py-3 text-left">
                    {h}
                  </th>
                ),
              )}
            </tr>
          </thead>
          <tbody className="divide-y">
            {(stats?.rooms ?? []).map(r => (
              <tr key={r.roomId} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{r.roomName}</td>
                <td className="px-4 py-3 text-gray-500">{r.capacity}</td>
                <td className="px-4 py-3">{r.checkedIn}</td>
                <td className="px-4 py-3">{r.percentage.toFixed(1)}%</td>
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
                    className="text-xs text-indigo-600 hover:underline"
                  >
                    View →
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {stats && (
        <p className="text-xs text-gray-400 text-right">
          Last updated: {new Date(stats.lastUpdated).toLocaleTimeString()}
        </p>
      )}
    </div>
  );
}
