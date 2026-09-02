import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

export default function MyUploadsPage() {
  const navigate = useNavigate();
  const [uploads, setUploads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchUploads = async () => {
      setLoading(true);
      try {
        const response = await axios.get(`${API_BASE_URL}/student/uploads`, {
          headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
        });
        setUploads(response.data.data || []);
      } catch (err) {
        setError(err.response?.data?.message || 'Unable to load your uploads.');
      } finally {
        setLoading(false);
      }
    };

    fetchUploads();
  }, []);

  if (loading) return <div className="min-h-screen p-8 text-center">Loading uploads...</div>;
  if (error) return <div className="min-h-screen p-8 text-center text-red-600">{error}</div>;

  const statusBadgeClass = (status) => {
    switch (status) {
      case 'ACCEPTED':
        return 'bg-green-100 text-green-800';
      case 'REJECTED':
        return 'bg-red-100 text-red-800';
      case 'PENDING_REVIEW':
      case 'PROCESSING':
        return 'bg-amber-100 text-amber-800';
      default:
        return 'bg-slate-100 text-slate-700';
    }
  };

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-5xl card">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-3xl font-bold">My uploads</h1>
          <button className="btn-secondary" onClick={() => navigate('/student')}>Back</button>
        </div>

        {uploads.length === 0 ? (
          <p className="text-slate-600">No uploads yet.</p>
        ) : (
          <div className="space-y-4">
            {uploads.map((upload) => (
              <div key={upload.id} className="rounded-xl border p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="font-semibold text-slate-900">{upload.paperTitle}</h3>
                    <p className="text-sm text-slate-500">{upload.fileName}</p>
                    <span
                      className={`mt-2 inline-block rounded-full px-3 py-1 text-xs font-semibold ${statusBadgeClass(upload.status)}`}
                    >
                      {upload.status}
                    </span>
                  </div>
                  {upload.paperId && upload.status !== 'REJECTED' && (
                    <button className="btn-primary" onClick={() => navigate(`/paper/${upload.paperId}`)}>View</button>
                  )}
                </div>
                {upload.reason && (
                  <p className="mt-3 text-sm text-slate-600">{upload.reason}</p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
