// src/pages/RoomDetail.tsx
import { useEffect, useRef, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getRoomDetail, SeatData } from "../api/roomDetail";
import { Client } from "@stomp/stompjs";
import clsx from "clsx";
import StudentReviewModal from "../components/StudentReviewModal";

type Filter = "all" | "checkedIn" | "notCheckedIn";

export default function RoomDetail() {
  const { id } = useParams<{ id: string }>();
  const [params] = useSearchParams();
  const day = params.get("day") || "day1";
  const [filter, setFilter] = useState<Filter>("all");
  const [search, setSearch] = useState("");
  const [tab, setTab] = useState<Filter>("all");
  // Fix 7 Part C — review modal state
  const [reviewStudent, setReviewStudent] = useState<SeatData | null>(null);
  const qc = useQueryClient();
  const stompRef = useRef<Client | null>(null);

  const { data: room } = useQuery({
    queryKey: ["roomDetail", id, day],
    queryFn: () => getRoomDetail(Number(id), day).then((r) => r.data),
  });

  // Replace the useEffect in RoomDetail.tsx:
  useEffect(() => {
    let client: Client | null = null;
    try {
      client = new Client({
        brokerURL: `ws://${window.location.host}/ws/websocket`,
        reconnectDelay: 5000,
        onConnect: () => {
          client?.subscribe(`/topic/room/${id}/checkins`, () => {
            qc.invalidateQueries({ queryKey: ["roomDetail", id, day] });
          });
        },
        onStompError: (frame) => console.warn("STOMP error", frame),
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
  }, [id, day, qc]);

  if (!room) return <div className="p-8 text-gray-400">Loading…</div>;

  const seats = room.seats;
  const seatMap = new Map(seats.map((s) => [s.seatNumber, s]));

  // Build grid
  const rows = Math.ceil(room.capacity / room.seatsPerRow);
  const grid: (SeatData | null)[][] = Array.from({ length: rows }, (_, r) =>
    Array.from({ length: room.seatsPerRow }, (_, c) => {
      const rowLabel = rowLetter(r);
      const seatNum = `${rowLabel}-${String(c + 1).padStart(2, "0")}`;
      return seatMap.get(seatNum) ?? null;
    }),
  );

  // Fix 7 Part B — compute how many leading seats are in reserved rows
  const totalSkippedSeats = (room.skipRows ?? 0) * room.seatsPerRow;

  // Candidate list
  const listData =
    tab === "checkedIn"
      ? seats.filter((s) => s.checkedIn)
      : tab === "notCheckedIn"
        ? seats.filter((s) => !s.checkedIn)
        : seats;
  const filtered = listData.filter(
    (s) =>
      !search ||
      s.name.toLowerCase().includes(search.toLowerCase()) ||
      s.regNo.includes(search),
  );

  const downloadCsv = () => {
    const rows = [
      [
        "RegNo",
        "Name",
        "Degree",
        "Passout",
        "Seat",
        "Check-in Time",
        "Verifier",
      ],
      ...filtered.map((s) => [
        s.regNo,
        s.name,
        s.degree,
        s.passoutYear,
        s.seatNumber ?? "No Seat",
        s.checkInTime ?? "",
        s.verifierUsername ?? "",
      ]),
    ];
    const csv = rows.map((r) => r.join(",")).join("\n");
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([csv]));
    a.download = `room-${id}-${tab}.csv`;
    a.click();
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold">{room.roomName}</h1>
        <p className="text-sm text-gray-500">
          {room.building} · {room.floor} · {day === "day1" ? "Day 1" : "Day 2"}{" "}
          · {room.capacity} seats
        </p>
      </div>

      {/* Seat Map */}
      <div className="bg-white rounded-xl border p-6 space-y-4">
        <div className="flex items-center gap-4">
          <h2 className="font-semibold text-sm">Seat Map</h2>
          {(["all", "checkedIn", "notCheckedIn"] as Filter[]).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`px-3 py-1 text-xs rounded-full ${filter === f ? "bg-indigo-600 text-white" : "bg-gray-100 text-gray-600"}`}
            >
              {f === "all"
                ? "All"
                : f === "checkedIn"
                  ? "Checked In"
                  : "Not Checked In"}
            </button>
          ))}
        </div>
        {/* Fix 7 Part B — Legend with Reserved entry */}
        <div className="flex gap-4 text-xs text-gray-500">
          {[
            ["bg-green-400", "Checked In"],
            ["bg-red-300", "Not Checked In"],
            ["bg-gray-200", "Empty"],
            ["bg-gray-200 opacity-40", "Reserved"],
          ].map(([cls, label]) => (
            <span key={label} className="flex items-center gap-1">
              <span className={`w-3 h-3 rounded-sm ${cls}`} />
              {label}
            </span>
          ))}
        </div>
        <div className="overflow-x-auto">
          <div
            className="grid gap-1"
            style={{
              gridTemplateColumns: `repeat(${room.seatsPerRow}, minmax(2rem, 1fr))`,
            }}
          >
            {grid.flat().map((seat, i) => {
              // Fix 7 Part B — greyed reserved seats (come BEFORE standard empty check)
              if (!seat && i < totalSkippedSeats) {
                return (
                  <div
                    key={i}
                    title="Reserved row"
                    className="w-8 h-8 rounded bg-gray-200 opacity-40
                               cursor-not-allowed flex items-center justify-center"
                  >
                    <span className="text-gray-400 text-xs">—</span>
                  </div>
                );
              }
              if (!seat)
                return <div key={i} className="w-8 h-8 rounded bg-gray-100" />;
              const hidden =
                (filter === "checkedIn" && !seat.checkedIn) ||
                (filter === "notCheckedIn" && seat.checkedIn);
              return (
                <div
                  key={i}
                  title={`${seat.name} (${seat.regNo})${seat.checkedIn ? " ✅ " + seat.checkInTime : ""}`}
                  className={clsx(
                    "w-8 h-8 rounded text-xs flex items-center justify-center cursor-pointer transition-opacity",
                    hidden ? "opacity-10" : "",
                    seat.checkedIn ? "bg-green-400" : "bg-red-300",
                  )}
                ></div>
              );
            })}
          </div>
        </div>
        {room.overflow.length > 0 && (
          <p className="text-xs text-yellow-600 font-medium">
            {room.overflow.length} overflow students (standing, no seat)
          </p>
        )}
      </div>

      {/* Candidate list */}
      <div className="bg-white rounded-xl border p-6 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex gap-2">
            {(["all", "checkedIn", "notCheckedIn"] as Filter[]).map((t) => (
              <button
                key={t}
                onClick={() => setTab(t)}
                className={`px-3 py-1 text-xs rounded-full ${tab === t ? "bg-indigo-600 text-white" : "bg-gray-100 text-gray-600"}`}
              >
                {t === "all"
                  ? `All (${seats.length})`
                  : t === "checkedIn"
                    ? `In (${seats.filter((s) => s.checkedIn).length})`
                    : `Out (${seats.filter((s) => !s.checkedIn).length})`}
              </button>
            ))}
          </div>
          <div className="flex gap-2">
            <input
              placeholder="Search name / regNo"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="border rounded-lg px-3 py-1.5 text-xs w-48"
            />
            <button
              onClick={downloadCsv}
              className="px-3 py-1.5 text-xs border rounded-lg hover:bg-gray-50"
            >
              Export CSV
            </button>
          </div>
        </div>
        <table className="w-full text-sm">
          <thead className="text-xs text-gray-500 uppercase bg-gray-50">
            <tr>
              {[
                "RegNo",
                "Name",
                "Degree",
                "Seat",
                "Check-in Time",
                "Verifier",
              ].map((h) => (
                <th key={h} className="px-3 py-2 text-left">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y">
            {filtered.slice(0, 100).map((s) => (
              // Fix 7 Part C — rows are clickable, open review modal
              <tr
                key={s.studentId}
                className="hover:bg-gray-50 cursor-pointer"
                onClick={() => setReviewStudent(s)}
              >
                <td className="px-3 py-2 font-mono text-xs">{s.regNo}</td>
                <td className="px-3 py-2">{s.name}</td>
                <td className="px-3 py-2 text-gray-500">{s.degree}</td>
                <td className="px-3 py-2">{s.seatNumber ?? "No Seat"}</td>
                <td className="px-3 py-2 text-xs text-gray-500">
                  {s.checkInTime
                    ? new Date(s.checkInTime).toLocaleTimeString()
                    : "—"}
                </td>
                <td className="px-3 py-2 text-xs text-gray-500">
                  {s.verifierUsername ?? "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Fix 7 Part C — StudentReviewModal */}
      {reviewStudent && reviewStudent.checkInId && (
        <StudentReviewModal
          studentId={reviewStudent.studentId}
          studentName={reviewStudent.name}
          regNo={reviewStudent.regNo}
          checkInId={reviewStudent.checkInId}
          day={day}
          onClose={() => setReviewStudent(null)}
        />
      )}
    </div>
  );
}

function rowLetter(index: number): string {
  let label = "";
  let i = index;
  do {
    label = String.fromCharCode(65 + (i % 26)) + label;
    i = Math.floor(i / 26) - 1;
  } while (i >= 0);
  return label;
}
