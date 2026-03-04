import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '../store/authStore';
import api from '../api/client';

interface VolunteerProfile {
  id: number;
  regNo: string;
  name: string;
  role: string;
  isPromoted: boolean;
}

export default function VolunteerDashboard() {
  const { logout } = useAuthStore();
  const [qrUrl, setQrUrl] = useState<string | null>(null);

  const { data: profile } = useQuery({
    queryKey: ['volunteerProfile'],
    queryFn: () => api.get<VolunteerProfile>('/student/profile').then(r => r.data),
  });

  // Fetch QR as blob and create an object URL
  useEffect(() => {
    api.get('/student/qr', { responseType: 'blob' })
      .then(r => setQrUrl(URL.createObjectURL(r.data)))
      .catch(() => {});
    return () => { if (qrUrl) URL.revokeObjectURL(qrUrl); };
  }, []);

  const handleDownload = () => {
    if (!qrUrl || !profile) return;
    const a = document.createElement('a');
    a.href = qrUrl;
    a.download = `volunteer-pass-${profile.regNo}.png`;
    a.click();
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Header */}
      <div className="bg-white border-b px-4 py-3 flex items-center justify-between">
        <div>
          <h1 className="font-semibold text-gray-800">Volunteer Portal</h1>
          <p className="text-xs text-gray-400">Venue Management System</p>
        </div>
        <button onClick={logout} className="text-sm text-red-500 hover:text-red-700 font-medium">
          Logout
        </button>
      </div>

      <div className="max-w-sm mx-auto w-full px-4 py-8 space-y-6">
        {/* Profile Card */}
        {profile && (
          <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-600 font-bold text-lg">
                {profile.name.charAt(0).toUpperCase()}
              </div>
              <div>
                <p className="font-semibold text-gray-800">{profile.name}</p>
                <p className="text-sm text-gray-400">{profile.regNo}</p>
              </div>
              <span className={`ml-auto text-xs font-semibold px-2.5 py-1 rounded-full ${
                profile.isPromoted
                  ? 'bg-purple-100 text-purple-700'
                  : 'bg-indigo-100 text-indigo-700'
              }`}>
                {profile.isPromoted ? 'VERIFIER' : 'VOLUNTEER'}
              </span>
            </div>
          </div>
        )}

        {/* Validity Badge */}
        <div className="bg-indigo-600 rounded-xl p-4 text-white text-center shadow-sm">
          <p className="text-sm font-medium opacity-80">Valid For</p>
          <p className="text-xl font-bold mt-0.5">Day 1 & Day 2</p>
          <p className="text-xs opacity-70 mt-1">Show this QR at entry to get checked in</p>
        </div>

        {/* QR Code */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6 flex flex-col items-center gap-4">
          <p className="text-sm font-semibold text-gray-600">Your Identity QR</p>
          {qrUrl ? (
            <img
              src={qrUrl}
              alt="Volunteer QR"
              className="w-56 h-56 rounded-lg"
            />
          ) : (
            <div className="w-56 h-56 bg-gray-100 rounded-lg animate-pulse" />
          )}
          <button
            onClick={handleDownload}
            disabled={!qrUrl}
            className="w-full py-2.5 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-40 transition-colors"
          >
            Download QR Pass
          </button>
          <p className="text-xs text-gray-400 text-center">
            Present this QR to a verifier or admin to mark your attendance
          </p>
        </div>
      </div>
    </div>
  );
}