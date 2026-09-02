import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import FacultyHeader from '../components/FacultyHeader';

const API_BASE_URL = 'http://localhost:8080/api';

export default function QuestionPaperDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [paper, setPaper] = useState(null);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [regeneratingId, setRegeneratingId] = useState(null);

  const authHeaders = () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` });
  const isEditable = paper?.status === 'DRAFT';

  const loadPaper = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE_URL}/faculty/question-papers/${id}`, { headers: authHeaders() });
      setPaper(response.data.data);
      setItems(response.data.data.items || []);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load this question paper.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPaper();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const persistItems = async (nextItems) => {
    setSaving(true);
    try {
      const response = await axios.put(
        `${API_BASE_URL}/faculty/question-papers/${id}`,
        { items: nextItems.map((item, idx) => ({ id: item.id, marks: item.marks, orderIndex: idx + 1 })) },
        { headers: authHeaders() }
      );
      setPaper(response.data.data);
      setItems(response.data.data.items || []);
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to save changes.');
    } finally {
      setSaving(false);
    }
  };

  const handleRemoveItem = (itemId) => {
    const next = items.filter((i) => i.id !== itemId);
    setItems(next);
    persistItems(next);
  };

  const handleMarksChange = (itemId, marks) => {
    setItems((prev) => prev.map((i) => (i.id === itemId ? { ...i, marks: Number(marks) } : i)));
  };

  const handleRegenerate = async (itemId) => {
    setRegeneratingId(itemId);
    try {
      const response = await axios.post(
        `${API_BASE_URL}/faculty/question-papers/${id}/items/${itemId}/regenerate`,
        null,
        { headers: authHeaders() }
      );
      setItems((prev) => prev.map((i) => (i.id === itemId ? response.data.data : i)));
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to regenerate this question.');
    } finally {
      setRegeneratingId(null);
    }
  };

  const handleFinalize = async () => {
    try {
      await axios.post(`${API_BASE_URL}/faculty/question-papers/${id}/finalize`, null, { headers: authHeaders() });
      await loadPaper();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to finalize this paper.');
    }
  };

  const sourceBadge = (source) =>
    source === 'AI_GENERATED' ? (
      <span className="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-semibold text-purple-700">AI Generated</span>
    ) : (
      <span className="rounded-full bg-green-100 px-2 py-0.5 text-xs font-semibold text-green-700">Question Bank</span>
    );

  if (loading) return <div className="min-h-screen p-8 text-center">Loading…</div>;
  if (error && !paper) return <div className="min-h-screen p-8 text-center text-red-600">{error}</div>;

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-5xl">
        <FacultyHeader />

        <div className="card">
          <div className="mb-4 flex items-center justify-between">
            <button className="btn-secondary" onClick={() => navigate('/faculty/question-papers')}>← Back</button>
            {isEditable && (
              <button className="btn-primary" onClick={handleFinalize} disabled={items.length === 0}>
                Finalize
              </button>
            )}
          </div>

          <h2 className="text-xl font-semibold">{paper.examName}</h2>
          <p className="text-sm text-slate-600">
            {paper.collegeName} · {paper.subjectName} · {paper.examDate || 'No date set'} · {paper.totalMarks} marks ·{' '}
            <span
              className={`font-semibold uppercase ${paper.status === 'FINALIZED' ? 'text-green-600' : 'text-amber-600'}`}
            >
              {paper.status}
            </span>
          </p>
          {paper.instructions && <p className="mt-2 text-sm italic text-slate-500">"{paper.instructions}"</p>}

          {error && <div className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</div>}

          <div className="mt-6 space-y-3">
            {items.map((item, idx) => (
              <div key={item.id} className="rounded-xl border border-slate-200 p-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1">
                    <p className="text-sm text-slate-500">
                      Q{idx + 1} · Topic: {item.topic} · Difficulty: {item.difficulty}
                    </p>
                    <p className="mt-1 text-slate-900">{item.questionText}</p>
                    <div className="mt-2 flex items-center gap-3">
                      {sourceBadge(item.source)}
                      {isEditable && item.source === 'AI_GENERATED' && (
                        <button
                          className="text-sm font-medium text-blue-600"
                          disabled={regeneratingId === item.id}
                          onClick={() => handleRegenerate(item.id)}
                        >
                          {regeneratingId === item.id ? 'Regenerating…' : 'Regenerate'}
                        </button>
                      )}
                    </div>
                  </div>
                  <div className="flex flex-col items-end gap-2">
                    <label className="text-xs text-slate-500">Marks</label>
                    {isEditable ? (
                      <input
                        type="number"
                        min="1"
                        className="w-20 rounded-lg border border-slate-300 px-2 py-1 text-right"
                        value={item.marks}
                        onChange={(e) => handleMarksChange(item.id, e.target.value)}
                        onBlur={() => persistItems(items)}
                      />
                    ) : (
                      <span className="font-semibold">{item.marks}</span>
                    )}
                    {isEditable && (
                      <button className="text-sm font-medium text-red-600" onClick={() => handleRemoveItem(item.id)}>
                        Remove
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
          {saving && <p className="mt-3 text-sm text-slate-500">Saving…</p>}
        </div>
      </div>
    </div>
  );
}
