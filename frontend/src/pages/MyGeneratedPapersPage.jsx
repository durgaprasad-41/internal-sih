import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import FacultyHeader from '../components/FacultyHeader';

const API_BASE_URL = 'http://localhost:8080/api';

export default function MyGeneratedPapersPage() {
  const navigate = useNavigate();
  const [papers, setPapers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actioningId, setActioningId] = useState(null);

  const authHeaders = () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` });

  const loadPapers = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE_URL}/faculty/question-papers`, { headers: authHeaders() });
      setPapers(response.data.data || []);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load your generated papers.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPapers();
  }, []);

  const handleFinalize = async (id) => {
    setActioningId(id);
    try {
      await axios.post(`${API_BASE_URL}/faculty/question-papers/${id}/finalize`, null, { headers: authHeaders() });
      await loadPapers();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to finalize this paper.');
    } finally {
      setActioningId(null);
    }
  };

  const handleDelete = async (id) => {
    setActioningId(id);
    try {
      await axios.delete(`${API_BASE_URL}/faculty/question-papers/${id}`, { headers: authHeaders() });
      await loadPapers();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to delete this paper.');
    } finally {
      setActioningId(null);
    }
  };

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-6xl">
        <FacultyHeader />

        <div className="card">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-xl font-semibold">My generated papers ({papers.length})</h2>
            <button className="btn-secondary" onClick={loadPapers}>Refresh</button>
          </div>

          {error && <div className="mb-4 rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</div>}

          {loading ? (
            <p className="text-slate-500">Loading…</p>
          ) : papers.length === 0 ? (
            <div className="text-center text-slate-500">
              <p>You haven't generated any question papers yet.</p>
              <button className="btn-primary mt-4" onClick={() => navigate('/faculty/question-papers/generate')}>
                Generate your first question paper
              </button>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-700">
                  <tr>
                    <th className="px-4 py-3">Exam</th>
                    <th className="px-4 py-3">College</th>
                    <th className="px-4 py-3">Subject</th>
                    <th className="px-4 py-3">Exam date</th>
                    <th className="px-4 py-3">Total marks</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Created</th>
                    <th className="px-4 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {papers.map((paper) => (
                    <tr key={paper.id} className="border-t border-slate-200">
                      <td className="px-4 py-3 font-medium text-slate-900">{paper.examName}</td>
                      <td className="px-4 py-3">{paper.collegeName}</td>
                      <td className="px-4 py-3">{paper.subjectName}</td>
                      <td className="px-4 py-3">{paper.examDate || '—'}</td>
                      <td className="px-4 py-3">{paper.totalMarks}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`rounded-full px-2 py-1 text-xs font-semibold ${
                            paper.status === 'FINALIZED' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'
                          }`}
                        >
                          {paper.status}
                        </span>
                      </td>
                      <td className="px-4 py-3">{paper.createdAt ? paper.createdAt.substring(0, 10) : '—'}</td>
                      <td className="px-4 py-3">
                        <div className="flex flex-wrap gap-2">
                          <button
                            className="text-sm font-medium text-blue-600"
                            onClick={() => navigate(`/faculty/question-papers/${paper.id}`)}
                          >
                            View
                          </button>
                          {paper.status === 'DRAFT' && (
                            <>
                              <button
                                className="text-sm font-medium text-green-600"
                                disabled={actioningId === paper.id}
                                onClick={() => handleFinalize(paper.id)}
                              >
                                Finalize
                              </button>
                              <button
                                className="text-sm font-medium text-red-600"
                                disabled={actioningId === paper.id}
                                onClick={() => handleDelete(paper.id)}
                              >
                                Delete
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
