import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import FacultyHeader from '../components/FacultyHeader';

const API_BASE_URL = 'http://localhost:8080/api';

export default function FacultyReviewsPage() {
  const navigate = useNavigate();
  const [assignments, setAssignments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submittingId, setSubmittingId] = useState(null);
  const [commentDraft, setCommentDraft] = useState({});

  const authHeaders = () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` });

  const load = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE_URL}/faculty/reviews`, { headers: authHeaders() });
      setAssignments(response.data.data || []);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load your review assignments.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const submitDecision = async (assignmentId, decision) => {
    setSubmittingId(assignmentId);
    try {
      await axios.put(
        `${API_BASE_URL}/faculty/reviews/${assignmentId}`,
        { decision, comment: commentDraft[assignmentId] || '' },
        { headers: authHeaders() }
      );
      await load();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to submit your review.');
    } finally {
      setSubmittingId(null);
    }
  };

  const pending = assignments.filter((a) => a.status === 'PENDING');
  const completed = assignments.filter((a) => a.status !== 'PENDING');

  const statusBadge = (status) => {
    const map = { ACCEPT: 'bg-green-100 text-green-700', REJECT: 'bg-red-100 text-red-700' };
    return <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${map[status]}`}>{status}</span>;
  };

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-5xl">
        <FacultyHeader />

        <div className="card mb-6">
          <p className="text-sm text-slate-500">
            Papers an administrator has asked you to review. Your accept/reject is a recommendation for the admin -
            it does not publish or reject the paper by itself.
          </p>
        </div>

        {error && <div className="mb-4 rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</div>}

        <div className="card mb-6">
          <h2 className="mb-3 text-lg font-semibold">Awaiting your review ({pending.length})</h2>
          {loading ? (
            <p className="text-slate-500">Loading...</p>
          ) : pending.length === 0 ? (
            <p className="text-slate-500">Nothing needs your review right now.</p>
          ) : (
            <div className="space-y-4">
              {pending.map((a) => (
                <div key={a.id} className="rounded-xl border border-slate-200 p-4">
                  <h3 className="font-semibold text-slate-900">{a.paperTitle}</h3>
                  <p className="text-sm text-slate-600">Subject: {a.subjectName}</p>
                  <button
                    className="mt-2 text-sm font-medium text-blue-600 hover:underline"
                    onClick={() => navigate(`/paper/${a.paperId}`)}
                  >
                    View paper details
                  </button>
                  <textarea
                    className="mt-3 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    rows={2}
                    placeholder="Optional review comment"
                    value={commentDraft[a.id] || ''}
                    onChange={(e) => setCommentDraft((prev) => ({ ...prev, [a.id]: e.target.value }))}
                  />
                  <div className="mt-3 flex gap-2">
                    <button
                      className="btn-primary"
                      disabled={submittingId === a.id}
                      onClick={() => submitDecision(a.id, 'ACCEPT')}
                    >
                      Recommend accept
                    </button>
                    <button
                      className="btn-secondary"
                      disabled={submittingId === a.id}
                      onClick={() => submitDecision(a.id, 'REJECT')}
                    >
                      Recommend reject
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {completed.length > 0 && (
          <div className="card">
            <h2 className="mb-3 text-lg font-semibold">Your review history ({completed.length})</h2>
            <div className="space-y-3">
              {completed.map((a) => (
                <div key={a.id} className="flex items-center justify-between rounded-lg border border-slate-200 p-3">
                  <div>
                    <p className="font-medium text-slate-900">{a.paperTitle}</p>
                    {a.comment && <p className="text-sm text-slate-500">"{a.comment}"</p>}
                  </div>
                  {statusBadge(a.status)}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
