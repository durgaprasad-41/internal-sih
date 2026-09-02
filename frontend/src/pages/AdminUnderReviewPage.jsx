import { useEffect, useState } from 'react';
import axios from 'axios';
import AdminHeader from '../components/AdminHeader';

const API_BASE_URL = 'http://localhost:8080/api';

export default function AdminUnderReviewPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actioningId, setActioningId] = useState(null);
  const [rejectReasonDraft, setRejectReasonDraft] = useState({});

  const authHeaders = () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` });

  const load = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE_URL}/admin/papers/under-review`, { headers: authHeaders() });
      setItems(response.data.data || []);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load papers under faculty review.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleApprove = async (paperId) => {
    setActioningId(paperId);
    try {
      await axios.put(`${API_BASE_URL}/admin/papers/${paperId}/approve`, null, { headers: authHeaders() });
      await load();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to approve this paper.');
    } finally {
      setActioningId(null);
    }
  };

  const handleReject = async (paperId) => {
    const reason = (rejectReasonDraft[paperId] || '').trim();
    if (!reason) {
      setError('A rejection reason is required.');
      return;
    }
    setActioningId(paperId);
    try {
      await axios.put(`${API_BASE_URL}/admin/papers/${paperId}/reject`, null, {
        headers: authHeaders(),
        params: { reason }
      });
      await load();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to reject this paper.');
    } finally {
      setActioningId(null);
    }
  };

  const statusBadge = (status) => {
    const map = { ACCEPT: 'bg-green-100 text-green-700', REJECT: 'bg-red-100 text-red-700', PENDING: 'bg-slate-200 text-slate-600' };
    return <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${map[status] || map.PENDING}`}>{status}</span>;
  };

  const recommendationBanner = (summary) => {
    if (!summary.reviewComplete) {
      return (
        <p className="mt-2 text-sm text-slate-500">
          Awaiting {summary.totalAssigned - summary.responded} of {summary.totalAssigned} reviewer(s) to respond.
        </p>
      );
    }
    const map = {
      ACCEPT: { text: 'Recommended for acceptance', cls: 'bg-green-50 text-green-800 border-green-300' },
      REJECT: { text: 'Recommended for rejection', cls: 'bg-red-50 text-red-800 border-red-300' },
      MIXED: { text: 'Mixed feedback - no clear majority', cls: 'bg-amber-50 text-amber-800 border-amber-300' }
    };
    const rec = map[summary.recommendation] || map.MIXED;
    return (
      <p className={`mt-2 rounded-lg border px-3 py-2 text-sm font-medium ${rec.cls}`}>
        {rec.text} ({summary.acceptVotes} accept, {summary.rejectVotes} reject of {summary.totalAssigned})
      </p>
    );
  };

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-7xl">
        <AdminHeader />

        <div className="card">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-xl font-semibold">Under faculty review ({items.length})</h2>
            <button className="btn-secondary" onClick={load}>Refresh</button>
          </div>

          {error && <div className="mb-4 rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</div>}

          {loading ? (
            <p className="text-slate-500">Loading...</p>
          ) : items.length === 0 ? (
            <p className="text-slate-500">No papers are currently with faculty reviewers.</p>
          ) : (
            <div className="space-y-5">
              {items.map((summary) => (
                <div key={summary.paper.id} className="rounded-xl border border-slate-200 p-4">
                  <div className="flex flex-wrap items-start justify-between gap-4">
                    <div className="flex-1">
                      <h3 className="font-semibold text-slate-900">{summary.paper.title}</h3>
                      <p className="text-sm text-slate-600">
                        Subject: <span className="font-medium">{summary.paper.subjectName}</span>
                        {' · '}Uploaded by: <span className="font-medium">{summary.paper.uploaderUsername}</span>
                      </p>

                      {recommendationBanner(summary)}

                      <div className="mt-3 space-y-2">
                        {summary.assignments.map((a) => (
                          <div key={a.id} className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2 text-sm">
                            <div>
                              <span className="font-medium text-slate-800">{a.reviewerFullName || a.reviewerUsername}</span>
                              {a.comment && <p className="mt-1 text-xs text-slate-500">"{a.comment}"</p>}
                            </div>
                            {statusBadge(a.status)}
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="flex flex-col items-end gap-2">
                      <div className="flex gap-2">
                        <button
                          className="btn-primary"
                          disabled={actioningId === summary.paper.id}
                          onClick={() => handleApprove(summary.paper.id)}
                        >
                          Final: Accept
                        </button>
                        <button
                          className="btn-secondary"
                          disabled={actioningId === summary.paper.id}
                          onClick={() => handleReject(summary.paper.id)}
                        >
                          Final: Reject
                        </button>
                      </div>
                      <input
                        type="text"
                        placeholder="Rejection reason (required to reject)"
                        className="w-64 rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
                        value={rejectReasonDraft[summary.paper.id] || ''}
                        onChange={(e) =>
                          setRejectReasonDraft((prev) => ({ ...prev, [summary.paper.id]: e.target.value }))
                        }
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
