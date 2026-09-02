import { useEffect, useState } from 'react';
import axios from 'axios';
import AdminHeader from '../components/AdminHeader';

const API_BASE_URL = 'http://localhost:8080/api';

export default function AdminPendingApprovalsPage() {
  const [pendingPapers, setPendingPapers] = useState([]);
  const [facultyOptions, setFacultyOptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actioningId, setActioningId] = useState(null);
  const [rejectReasonDraft, setRejectReasonDraft] = useState({});
  const [reviewerSelection, setReviewerSelection] = useState({});
  const [forwardingId, setForwardingId] = useState(null);

  const authHeaders = () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` });

  const loadPendingPapers = async () => {
    setLoading(true);
    try {
      const [pendingResponse, usersResponse] = await Promise.all([
        axios.get(`${API_BASE_URL}/admin/papers/pending`, { headers: authHeaders() }),
        axios.get(`${API_BASE_URL}/admin/users`, { headers: authHeaders() })
      ]);
      setPendingPapers(pendingResponse.data.data || []);
      setFacultyOptions((usersResponse.data.data || []).filter((u) => u.role?.name === 'FACULTY'));
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load papers awaiting review.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPendingPapers();
  }, []);

  const toggleReviewer = (paperId, facultyId) => {
    setReviewerSelection((prev) => {
      const current = new Set(prev[paperId] || []);
      if (current.has(facultyId)) {
        current.delete(facultyId);
      } else {
        current.add(facultyId);
      }
      return { ...prev, [paperId]: Array.from(current) };
    });
  };

  const handleForwardToReviewers = async (paperId) => {
    const reviewerIds = reviewerSelection[paperId] || [];
    if (reviewerIds.length === 0) {
      setError('Select at least one faculty reviewer to forward this paper to.');
      return;
    }
    setForwardingId(paperId);
    try {
      await axios.post(`${API_BASE_URL}/admin/papers/${paperId}/assign-reviewers`, { reviewerIds }, {
        headers: authHeaders()
      });
      setReviewerSelection((prev) => ({ ...prev, [paperId]: [] }));
      await loadPendingPapers();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to forward this paper to faculty reviewers.');
    } finally {
      setForwardingId(null);
    }
  };

  const handleApprove = async (paperId) => {
    setActioningId(paperId);
    try {
      await axios.put(`${API_BASE_URL}/admin/papers/${paperId}/approve`, null, { headers: authHeaders() });
      await loadPendingPapers();
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
      await loadPendingPapers();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to reject this paper.');
    } finally {
      setActioningId(null);
    }
  };

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-7xl">
        <AdminHeader />

        <div className="card">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-xl font-semibold">Pending approvals ({pendingPapers.length})</h2>
            <button className="btn-secondary" onClick={loadPendingPapers}>Refresh</button>
          </div>

          {error && <div className="mb-4 rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</div>}

          {loading ? (
            <p className="text-slate-500">Loading...</p>
          ) : pendingPapers.length === 0 ? (
            <p className="text-slate-500">Nothing needs review right now.</p>
          ) : (
            <div className="space-y-4">
              {pendingPapers.map((paper) => (
                <div key={paper.id} className="rounded-xl border border-slate-200 p-4">
                  <div className="flex flex-wrap items-start justify-between gap-4">
                    <div>
                      <h3 className="font-semibold text-slate-900">{paper.title}</h3>
                      <p className="text-sm text-slate-600">
                        Subject: <span className="font-medium">{paper.subjectName}</span>
                        {' · '}Uploaded by: <span className="font-medium">{paper.uploaderUsername}</span>
                        {' · '}University: {paper.universityName}
                      </p>
                      {paper.reviewReason && (
                        <p className="mt-2 text-sm text-slate-700">
                          Validation result: {paper.reviewReason}
                          {paper.confidenceScore != null && ` (confidence: ${paper.confidenceScore.toFixed(2)})`}
                        </p>
                      )}
                      {paper.fileUrl && (
                        <a
                          href={`http://localhost:8080${paper.fileUrl}`}
                          target="_blank"
                          rel="noreferrer"
                          className="mt-2 inline-block text-sm font-medium text-blue-600 hover:underline"
                        >
                          View PDF
                        </a>
                      )}
                    </div>
                    <div className="flex flex-col items-end gap-2">
                      <div className="flex gap-2">
                        <button
                          className="btn-primary"
                          disabled={actioningId === paper.id}
                          onClick={() => handleApprove(paper.id)}
                        >
                          Accept
                        </button>
                        <button
                          className="btn-secondary"
                          disabled={actioningId === paper.id}
                          onClick={() => handleReject(paper.id)}
                        >
                          Reject
                        </button>
                      </div>
                      <input
                        type="text"
                        placeholder="Rejection reason (required to reject)"
                        className="w-64 rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
                        value={rejectReasonDraft[paper.id] || ''}
                        onChange={(e) =>
                          setRejectReasonDraft((prev) => ({ ...prev, [paper.id]: e.target.value }))
                        }
                      />
                    </div>
                  </div>

                  <div className="mt-4 rounded-lg border border-dashed border-slate-300 p-3">
                    <p className="mb-2 text-sm font-medium text-slate-700">
                      Or forward to faculty for expert review first (their accept/reject is a recommendation only -
                      you still make the final call):
                    </p>
                    {facultyOptions.length === 0 ? (
                      <p className="text-sm text-slate-500">No verified faculty accounts available yet.</p>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        {facultyOptions.map((faculty) => {
                          const selected = (reviewerSelection[paper.id] || []).includes(faculty.id);
                          return (
                            <button
                              key={faculty.id}
                              type="button"
                              onClick={() => toggleReviewer(paper.id, faculty.id)}
                              className={`rounded-full border px-3 py-1 text-xs font-medium ${
                                selected
                                  ? 'border-blue-600 bg-blue-600 text-white'
                                  : 'border-slate-300 bg-white text-slate-700'
                              }`}
                            >
                              {faculty.fullName || faculty.username}
                            </button>
                          );
                        })}
                      </div>
                    )}
                    <button
                      className="btn-secondary mt-3"
                      disabled={forwardingId === paper.id || (reviewerSelection[paper.id] || []).length === 0}
                      onClick={() => handleForwardToReviewers(paper.id)}
                    >
                      {forwardingId === paper.id ? 'Forwarding...' : 'Forward to selected reviewers'}
                    </button>
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
