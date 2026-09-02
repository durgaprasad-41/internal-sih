import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import FacultyHeader from '../components/FacultyHeader';

const API_BASE_URL = 'http://localhost:8080/api';

const emptyTopic = () => ({ topicName: '', numberOfQuestions: '' });

export default function GenerateQuestionPaperPage() {
  const navigate = useNavigate();

  const [form, setForm] = useState({
    collegeName: '',
    examName: '',
    examDate: '',
    subject: '',
    totalMarks: '',
    easyPercent: 30,
    mediumPercent: 50,
    hardPercent: 20,
    instructions: ''
  });
  const [topics, setTopics] = useState([emptyTopic()]);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState('');
  const [paper, setPaper] = useState(null);
  const [items, setItems] = useState([]);
  const [saving, setSaving] = useState(false);
  const [regeneratingId, setRegeneratingId] = useState(null);

  const authHeaders = () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` });

  const updateTopic = (index, field, value) => {
    setTopics((prev) => prev.map((t, i) => (i === index ? { ...t, [field]: value } : t)));
  };

  const addTopic = () => setTopics((prev) => [...prev, emptyTopic()]);
  const removeTopic = (index) => setTopics((prev) => prev.filter((_, i) => i !== index));

  const handleGenerate = async (e) => {
    e.preventDefault();
    setError('');
    const cleanedTopics = topics
      .filter((t) => t.topicName.trim() && Number(t.numberOfQuestions) > 0)
      .map((t) => ({ topicName: t.topicName.trim(), numberOfQuestions: Number(t.numberOfQuestions) }));

    if (cleanedTopics.length === 0) {
      setError('Add at least one topic with a number of questions greater than 0.');
      return;
    }
    if (!form.totalMarks || Number(form.totalMarks) <= 0) {
      setError('Total marks must be a positive number.');
      return;
    }

    setGenerating(true);
    try {
      const response = await axios.post(
        `${API_BASE_URL}/faculty/question-papers/generate`,
        {
          collegeName: form.collegeName,
          examName: form.examName,
          examDate: form.examDate || null,
          subject: form.subject,
          topics: cleanedTopics,
          totalMarks: Number(form.totalMarks),
          easyPercent: Number(form.easyPercent),
          mediumPercent: Number(form.mediumPercent),
          hardPercent: Number(form.hardPercent),
          instructions: form.instructions
        },
        { headers: authHeaders() }
      );
      const data = response.data.data;
      setPaper(data);
      setItems(data.items || []);
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to generate the question paper.');
    } finally {
      setGenerating(false);
    }
  };

  const persistItems = async (nextItems) => {
    if (!paper) return;
    setSaving(true);
    try {
      const response = await axios.put(
        `${API_BASE_URL}/faculty/question-papers/${paper.id}`,
        {
          items: nextItems.map((item, idx) => ({ id: item.id, marks: item.marks, orderIndex: idx + 1 }))
        },
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

  const handleMarksBlur = () => {
    persistItems(items);
  };

  const handleRegenerate = async (itemId) => {
    setRegeneratingId(itemId);
    setError('');
    try {
      const response = await axios.post(
        `${API_BASE_URL}/faculty/question-papers/${paper.id}/items/${itemId}/regenerate`,
        null,
        { headers: authHeaders() }
      );
      const updatedItem = response.data.data;
      setItems((prev) => prev.map((i) => (i.id === itemId ? updatedItem : i)));
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to regenerate this question.');
    } finally {
      setRegeneratingId(null);
    }
  };

  const handleFinalize = async () => {
    if (!paper) return;
    try {
      await axios.post(`${API_BASE_URL}/faculty/question-papers/${paper.id}/finalize`, null, {
        headers: authHeaders()
      });
      navigate('/faculty/question-papers');
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to finalize the question paper.');
    }
  };

  const sourceBadge = (source) =>
    source === 'AI_GENERATED' ? (
      <span className="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-semibold text-purple-700">AI Generated</span>
    ) : (
      <span className="rounded-full bg-green-100 px-2 py-0.5 text-xs font-semibold text-green-700">Question Bank</span>
    );

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-5xl">
        <FacultyHeader />

        {!paper && (
          <form onSubmit={handleGenerate} className="card space-y-6">
            <h2 className="text-xl font-semibold">Generate a question paper</h2>

            {error && <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</div>}

            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">College name</label>
                <input
                  required
                  className="w-full rounded-lg border border-slate-300 px-3 py-2"
                  value={form.collegeName}
                  onChange={(e) => setForm({ ...form, collegeName: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">Exam name / purpose</label>
                <input
                  required
                  className="w-full rounded-lg border border-slate-300 px-3 py-2"
                  placeholder="e.g., Mid Semester Examination"
                  value={form.examName}
                  onChange={(e) => setForm({ ...form, examName: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">Exam date</label>
                <input
                  type="date"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2"
                  value={form.examDate}
                  onChange={(e) => setForm({ ...form, examDate: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">Subject</label>
                <input
                  required
                  className="w-full rounded-lg border border-slate-300 px-3 py-2"
                  placeholder="Must match an existing subject, e.g., Database Management Systems"
                  value={form.subject}
                  onChange={(e) => setForm({ ...form, subject: e.target.value })}
                />
              </div>
            </div>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <label className="block text-sm font-medium text-slate-700">Topics / units</label>
                <button type="button" className="text-sm font-medium text-blue-600" onClick={addTopic}>
                  + Add topic
                </button>
              </div>
              <div className="space-y-2">
                {topics.map((topic, index) => (
                  <div key={index} className="flex gap-2">
                    <input
                      className="flex-1 rounded-lg border border-slate-300 px-3 py-2"
                      placeholder="Topic name"
                      value={topic.topicName}
                      onChange={(e) => updateTopic(index, 'topicName', e.target.value)}
                    />
                    <input
                      type="number"
                      min="0"
                      className="w-40 rounded-lg border border-slate-300 px-3 py-2"
                      placeholder="No. of questions"
                      value={topic.numberOfQuestions}
                      onChange={(e) => updateTopic(index, 'numberOfQuestions', e.target.value)}
                    />
                    {topics.length > 1 && (
                      <button
                        type="button"
                        className="rounded-lg border border-slate-300 px-3 text-slate-500"
                        onClick={() => removeTopic(index)}
                      >
                        ✕
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>

            <div className="grid gap-4 md:grid-cols-4">
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">Total marks</label>
                <input
                  type="number"
                  required
                  min="1"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2"
                  value={form.totalMarks}
                  onChange={(e) => setForm({ ...form, totalMarks: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">Easy %</label>
                <input
                  type="number"
                  min="0"
                  max="100"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2"
                  value={form.easyPercent}
                  onChange={(e) => setForm({ ...form, easyPercent: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">Medium %</label>
                <input
                  type="number"
                  min="0"
                  max="100"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2"
                  value={form.mediumPercent}
                  onChange={(e) => setForm({ ...form, mediumPercent: e.target.value })}
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700">Hard %</label>
                <input
                  type="number"
                  min="0"
                  max="100"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2"
                  value={form.hardPercent}
                  onChange={(e) => setForm({ ...form, hardPercent: e.target.value })}
                />
              </div>
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Optional instructions</label>
              <textarea
                className="w-full rounded-lg border border-slate-300 px-3 py-2"
                rows={3}
                placeholder='e.g., "Include more numerical problems from Unit 3."'
                value={form.instructions}
                onChange={(e) => setForm({ ...form, instructions: e.target.value })}
              />
            </div>

            <button type="submit" className="btn-primary" disabled={generating}>
              {generating ? 'Generating…' : 'Generate'}
            </button>
          </form>
        )}

        {generating && (
          <div className="card mt-6 text-center text-slate-500">Generating your question paper…</div>
        )}

        {paper && (
          <div className="card">
            <div className="mb-4 flex items-start justify-between">
              <div>
                <h2 className="text-xl font-semibold">{paper.examName}</h2>
                <p className="text-sm text-slate-600">
                  {paper.collegeName} · {paper.subjectName} · {paper.examDate || 'No date set'} · {paper.totalMarks} marks ·{' '}
                  <span className="font-semibold uppercase text-amber-600">{paper.status}</span>
                </p>
              </div>
              <span className="rounded-full bg-slate-100 px-3 py-1 text-sm font-medium text-slate-700">
                {items.length} questions
              </span>
            </div>

            {error && <div className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</div>}

            <div className="space-y-3">
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
                        {item.source === 'AI_GENERATED' && (
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
                      <input
                        type="number"
                        min="1"
                        className="w-20 rounded-lg border border-slate-300 px-2 py-1 text-right"
                        value={item.marks}
                        onChange={(e) => handleMarksChange(item.id, e.target.value)}
                        onBlur={handleMarksBlur}
                      />
                      <button
                        className="text-sm font-medium text-red-600"
                        onClick={() => handleRemoveItem(item.id)}
                      >
                        Remove
                      </button>
                    </div>
                  </div>
                </div>
              ))}
              {items.length === 0 && <p className="text-slate-500">All questions have been removed from this draft.</p>}
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              <button className="btn-primary" onClick={handleFinalize} disabled={saving || items.length === 0}>
                Finalize
              </button>
              <button className="btn-secondary" onClick={() => navigate('/faculty/question-papers')}>
                Save as draft &amp; view all
              </button>
              {saving && <span className="self-center text-sm text-slate-500">Saving…</span>}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
