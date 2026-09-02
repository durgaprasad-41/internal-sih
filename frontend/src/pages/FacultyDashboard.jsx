import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import FacultyHeader from '../components/FacultyHeader';

const API_BASE_URL = 'http://localhost:8080/api';

export default function FacultyDashboard() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const facultyName = localStorage.getItem('fullName') || localStorage.getItem('username');

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const response = await axios.get(`${API_BASE_URL}/faculty/dashboard`, {
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
    load();
  }, []);

  const cards = [
    { label: 'Papers uploaded', value: summary?.papersUploaded },
    { label: 'Approved uploads', value: summary?.approvedUploads },
    { label: 'Pending review', value: summary?.pendingUploads },
    { label: 'Verification status', value: summary?.verification, isText: true }
  ];

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-7xl">
        <FacultyHeader />

        <div className="mb-8 rounded-2xl bg-white p-6 shadow-sm">
          <h2 className="text-2xl font-bold text-slate-900">Welcome back, {facultyName}</h2>
          <p className="mt-1 text-slate-500">Manage your uploads and generate question papers for upcoming exams.</p>
        </div>

        {error && <div className="mb-6 rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</div>}

        <div className="mb-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {cards.map((card) => (
            <div key={card.label} className="card">
              <p className="text-sm text-slate-500">{card.label}</p>
              <p className={`mt-2 font-bold ${card.isText ? 'text-xl' : 'text-3xl'}`}>
                {loading ? '…' : summary ? card.value : '—'}
              </p>
            </div>
          ))}
        </div>

        <div className="card">
          <h3 className="mb-4 text-lg font-semibold text-slate-900">Quick actions</h3>
          <div className="flex flex-wrap gap-3">
            <button className="btn-primary" onClick={() => navigate('/upload')}>Upload a paper</button>
            <button className="btn-secondary" onClick={() => navigate('/faculty/uploads')}>My uploads</button>
            <button className="btn-secondary" onClick={() => navigate('/search')}>Search papers</button>
            <button className="btn-primary" onClick={() => navigate('/faculty/question-papers/generate')}>
              Generate question paper
            </button>
            <button className="btn-secondary" onClick={() => navigate('/faculty/question-papers')}>
              My generated papers
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
