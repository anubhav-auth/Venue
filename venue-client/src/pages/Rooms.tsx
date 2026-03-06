// src/pages/Rooms.tsx
import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getRooms,
  createRoom,
  updateRoom,
  deleteRoom,
  uploadRoomCsv,
  Room,
  pollRoomUploadStatus,
} from "@/api/rooms";
import CsvUploadZone from "@/components/CsvUploadZone";
import toast from "react-hot-toast";
import { Plus, Edit2, Trash2 } from "lucide-react";
import { useCsvUpload } from "../hooks/useCsvUpload";

const DAYS = ["day1", "day2"] as const;
const emptyForm: {
  roomName: string;
  building: string;
  floor: string;
  capacity: number;
  seatsPerRow: number;
  day: "day1" | "day2";
} = {
  roomName: "",
  building: "",
  floor: "",
  capacity: 0,
  seatsPerRow: 10,
  day: "day1",
};

export default function Rooms() {
  const [day, setDay] = useState<"day1" | "day2">("day1");
  const [modal, setModal] = useState<{ open: boolean; room?: Room }>({
    open: false,
  });
  const [form, setForm] = useState(emptyForm);
  const qc = useQueryClient();
  const roomUpload = useCsvUpload(uploadRoomCsv, pollRoomUploadStatus, [
    "rooms",
    day,
  ]);
  const { data: rooms = [] } = useQuery({
    queryKey: ["rooms", day],
    queryFn: () => getRooms(day).then((r) => r.data),
  });

  const save = useMutation({
    mutationFn: () =>
      modal.room ? updateRoom(modal.room.id, form) : createRoom(form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rooms"] });
      setModal({ open: false });
      toast.success("Saved");
    },
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteRoom(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rooms"] });
      toast.success("Deleted");
    },
    onError: () => toast.error("Cannot delete room with existing allocations"),
  });

  const openEdit = (room: Room) => {
    setForm({
      roomName: room.roomName,
      building: room.building,
      floor: room.floor,
      capacity: room.capacity,
      seatsPerRow: room.seatsPerRow,
      day: room.day,
    });
    setModal({ open: true, room });
  };
  const openNew = () => {
    setForm({ ...emptyForm, day });
    setModal({ open: true });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Rooms</h1>
        <button
          onClick={openNew}
          className="flex items-center gap-2 bg-indigo-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-indigo-700"
        >
          <Plus size={14} /> Add Room
        </button>
      </div>

      {/* Day tabs */}
      <div className="flex gap-2">
        {DAYS.map((d) => (
          <button
            key={d}
            onClick={() => setDay(d)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium ${day === d ? "bg-indigo-600 text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}
          >
            {d === "day1" ? "Day 1" : "Day 2"}
          </button>
        ))}
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
            <tr>
              {["Room", "Building", "Floor", "Capacity", "Seats/Row", ""].map(
                (h) => (
                  <th key={h} className="px-4 py-3 text-left">
                    {h}
                  </th>
                ),
              )}
            </tr>
          </thead>
          <tbody className="divide-y">
            {rooms.map((r) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{r.roomName}</td>
                <td className="px-4 py-3 text-gray-500">{r.building}</td>
                <td className="px-4 py-3 text-gray-500">{r.floor}</td>
                <td className="px-4 py-3">{r.capacity}</td>
                <td className="px-4 py-3">{r.seatsPerRow}</td>
                <td className="px-4 py-3 flex gap-2 justify-end">
                  <button
                    onClick={() => openEdit(r)}
                    className="p-1 text-gray-400 hover:text-indigo-600"
                  >
                    <Edit2 size={14} />
                  </button>
                  <button
                    onClick={() => {
                      if (confirm("Delete?")) remove.mutate(r.id);
                    }}
                    className="p-1 text-gray-400 hover:text-red-500"
                  >
                    <Trash2 size={14} />
                  </button>
                </td>
              </tr>
            ))}
            {rooms.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                  No rooms yet
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* CSV bulk import */}
      <div className="bg-white rounded-xl border p-6">
        <h2 className="text-sm font-semibold mb-3">Bulk Import via CSV</h2>
        <CsvUploadZone label="Rooms" onUpload={roomUpload.upload} />
        {roomUpload.status === "processing" && (
          <p className="text-xs text-indigo-500 mt-2 animate-pulse">
            ⏳ Processing in background…
          </p>
        )}
        {roomUpload.status === "done" && (
          <p className="text-xs text-green-600 mt-2">{roomUpload.msg}</p>
        )}
        {roomUpload.status === "error" && (
          <p className="text-xs text-red-500 mt-2">{roomUpload.msg}</p>
        )}
      </div>

      {/* Modal */}
      {modal.open && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md p-6 space-y-4">
            <h2 className="font-semibold">
              {modal.room ? "Edit Room" : "Add Room"}
            </h2>
            {(["roomName", "building", "floor"] as const).map((f) => (
              <input
                key={f}
                placeholder={f}
                value={(form as any)[f]}
                onChange={(e) =>
                  setForm((p) => ({ ...p, [f]: e.target.value }))
                }
                className="w-full border rounded-lg px-3 py-2 text-sm"
              />
            ))}
            <div className="grid grid-cols-2 gap-3">
              <input
                type="number"
                placeholder="Capacity"
                value={form.capacity}
                onChange={(e) =>
                  setForm((p) => ({ ...p, capacity: +e.target.value }))
                }
                className="border rounded-lg px-3 py-2 text-sm"
              />
              <input
                type="number"
                placeholder="Seats/Row"
                value={form.seatsPerRow}
                onChange={(e) =>
                  setForm((p) => ({ ...p, seatsPerRow: +e.target.value }))
                }
                className="border rounded-lg px-3 py-2 text-sm"
              />
            </div>
            <select
              value={form.day}
              onChange={(e) =>
                setForm((p) => ({ ...p, day: e.target.value as any }))
              }
              className="w-full border rounded-lg px-3 py-2 text-sm"
            >
              <option value="day1">Day 1</option>
              <option value="day2">Day 2</option>
            </select>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setModal({ open: false })}
                className="px-4 py-2 text-sm border rounded-lg"
              >
                Cancel
              </button>
              <button
                onClick={() => save.mutate()}
                disabled={save.isPending}
                className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-60"
              >
                {save.isPending ? "Saving…" : "Save"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
