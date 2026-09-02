import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import AdminHeader from '../components/AdminHeader';

const API_BASE_URL = 'http://localhost:8080/api';

export default function AdminDashboard() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const adminName = localStorage.getItem('fullName') || localStorage.getItem('username');

  const loadSummary = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE_URL}/admin/dashboard`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
      });
      setSummary(response.data.data);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load dashboard summary.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSummary();
  }, []);

  const cards = [
    { label: 'Papers awaiting review', value: summary?.pendingReview, accent: 'bg-amber-50 text-amber-700' },
    { label: 'Accepted papers', value: summary?.accepted, accent: 'bg-green-50 text-green-700' },
    { label: 'Rejected papers', value: summary?.rejected, accent: 'bg-red-50 text-red-700' },
    { label: 'Total papers', value: summary?.totalPapers, accent: 'bg-blue-50 text-blue-700' }
  ];

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-7xl">
        <AdminHeader />

        <div className="mb-8 rounded-2xl bg-white p-6 shadow-sm">
          <h2 className="text-2xl font-bold text-slate-900">Welcome back, {adminName}</h2>
          <p className="mt-1 text-slate-500">Here's what's happening with paper submissions right now.</p>
        </div>

        {error && <div className="mb-6 rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</div>}

        <div className="mb-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {cards.map((card) => (
            <div key={card.label} className="card">
              <p className="text-sm text-slate-500">{card.label}</p>
              <p className={`mt-2 inline-block rounded-lg px-2 py-1 text-3xl font-bold ${card.accent}`}>
                {loading ? '…' : summary ? card.value : '—'}
              </p>
            </div>
          ))}
        </div>

        <div className="card">
          <h3 className="mb-4 text-lg font-semibold text-slate-900">Quick actions</h3>
          <div className="flex flex-wrap gap-3">
            <button className="btn-primary" onClick={() => navigate('/admin/pending-approvals')}>
              Review pending papers{summary?.pendingReview ? ` (${summary.pendingReview})` : ''}
            </button>
            <button className="btn-secondary" onClick={() => navigate('/admin/notifications')}>
              View notifications
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
