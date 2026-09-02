import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

export default function SmartRevisionPage() {
  const navigate = useNavigate();

  const [subject, setSubject] = useState('');
  const [topicsText, setTopicsText] = useState('');
  const [availableHours, setAvailableHours] = useState('');
  const [targetMarks, setTargetMarks] = useState('');
  const [examDate, setExamDate] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [plan, setPlan] = useState(null);

  const priorityBadge = (priority) => {
    const map = {
      HIGH: 'bg-red-100 text-red-700',
      MEDIUM: 'bg-amber-100 text-amber-700',
      LOW: 'bg-yellow-100 text-yellow-700',
      NO_DATA: 'bg-slate-200 text-slate-600'
    };
    const emoji = { HIGH: '🔴', MEDIUM: '🟠', LOW: '🟡', NO_DATA: '⚪' };
    return (
      <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${map[priority] || map.NO_DATA}`}>
        {emoji[priority] || ''} {priority.replace('_', ' ')}
      </span>
    );
  };

  const TIERS = [
    { key: 'MUST_STUDY', label: 'Must Study', emoji: '🔥', accent: 'border-red-400 bg-red-50' },
    { key: 'HIGH_PRIORITY', label: 'High Priority', emoji: '⭐', accent: 'border-amber-400 bg-amber-50' },
    { key: 'SYLLABUS_COVERAGE', label: 'Syllabus Coverage', emoji: '📘', accent: 'border-blue-300 bg-blue-50' }
  ];

  const groupByTier = (questions) => {
    const groups = { MUST_STUDY: [], HIGH_PRIORITY: [], SYLLABUS_COVERAGE: [] };
    questions.forEach((q) => {
      (groups[q.tier] || groups.HIGH_PRIORITY).push(q);
    });
    return groups;
  };

  const groupByMarks = (questions) => {
    const groups = {};
    questions.forEach((q) => {
      const key = q.marks != null ? String(q.marks) : 'Unspecified';
      groups[key] = groups[key] || [];
      groups[key].push(q);
    });
    return Object.entries(groups).sort((a, b) => {
      if (a[0] === 'Unspecified') return 1;
      if (b[0] === 'Unspecified') return -1;
      return Number(a[0]) - Number(b[0]);
    });
  };

  const handleGenerate = async (e) => {
    e.preventDefault();
    setError('');
    const topics = topicsText
      .split(/[,\n]/)
      .map((t) => t.trim())
      .filter(Boolean);

    if (!subject.trim()) {
      setError('Enter a subject.');
      return;
    }
    if (topics.length === 0) {
      setError('Enter at least one topic.');
      return;
    }
    const minutes = Math.round(Number(availableHours) * 60);
    if (!minutes || minutes <= 0) {
      setError('Enter your available study time in hours (e.g., 4 or 1.5).');
      return;
    }

    setLoading(true);
    const payload = {
      subject: subject.trim(),
      topics,
      availableMinutes: minutes,
      targetMarks: targetMarks ? Number(targetMarks) : null,
      examDate: examDate || null
    };
    try {
      console.log('[SmartRevisionPage] POST /student/smart-revision', payload);
      const response = await axios.post(`${API_BASE_URL}/student/smart-revision`, payload, {
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
      });
      console.log('[SmartRevisionPage] response', response.data);
      setPlan(response.data.data);
    } catch (err) {
      console.error('[SmartRevisionPage] request failed', err);
      if (err.response) {
        // The server responded with an error (e.g. unknown subject) - show
        // its actual message, never a generic "network error" for this case.
        setError(err.response.data?.message || `Server returned ${err.response.status}.`);
      } else if (err.request) {
        setError('Could not reach the server. Check your connection and that the backend is running, then try again.');
      } else {
        setError('Unable to generate a revision plan.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-5xl">
        <div className="mb-8 flex items-center justify-between rounded-2xl bg-slate-900 px-6 py-4 text-white">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] text-blue-300">EXAMIQ</p>
            <h1 className="mt-1 text-2xl font-bold">Smart Revision · Probable Question Predictor</h1>
          </div>
          <button className="btn-secondary bg-slate-800 text-white border-slate-700" onClick={() => navigate('/student')}>
            Back to dashboard
          </button>
        </div>

        <form onSubmit={handleGenerate} className="card mb-6 space-y-4">
          <p className="text-sm text-slate-500">
            Short on time before an exam? Tell us your subject, the topics you still need to cover, and how much
            time you have - we'll rank the most probable questions to study, based on how often similar questions
            appeared in real approved papers. These are data-driven priorities, not a guarantee of the exact
            questions that will appear.
          </p>
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Subject</label>
              <input
                required
                className="w-full rounded-lg border border-slate-300 px-3 py-2"
                placeholder="Must match an existing subject, e.g., Engineering Chemistry"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Exam date (optional)</label>
              <input
                type="date"
                className="w-full rounded-lg border border-slate-300 px-3 py-2"
                value={examDate}
                onChange={(e) => setExamDate(e.target.value)}
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Topics to cover</label>
            <textarea
              required
              className="w-full rounded-lg border border-slate-300 px-3 py-2"
              rows={3}
              placeholder="One topic per line or comma-separated, e.g.:&#10;Electrochemistry&#10;Polymers&#10;Corrosion"
              value={topicsText}
              onChange={(e) => setTopicsText(e.target.value)}
            />
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Available study time (hours)</label>
              <input
                type="number"
                required
                min="0.5"
                step="0.5"
                className="w-full rounded-lg border border-slate-300 px-3 py-2"
                placeholder="e.g., 6"
                value={availableHours}
                onChange={(e) => setAvailableHours(e.target.value)}
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Target marks (optional)</label>
              <input
                type="number"
                min="0"
                className="w-full rounded-lg border border-slate-300 px-3 py-2"
                value={targetMarks}
                onChange={(e) => setTargetMarks(e.target.value)}
              />
            </div>
          </div>
          {error && <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</div>}
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? 'Analyzing…' : 'Generate revision plan'}
          </button>
        </form>

        {plan && (
          <>
            <div className="card mb-6">
              <h2 className="mb-3 text-lg font-semibold">Revision summary</h2>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <div>
                  <p className="text-sm text-slate-500">Available time</p>
                  <p className="text-2xl font-bold">{Math.round(plan.availableMinutes / 6) / 10}h</p>
                </div>
                <div>
                  <p className="text-sm text-slate-500">Topics covered</p>
                  <p className="text-2xl font-bold">{plan.topicsCovered} / {plan.topicsSelected}</p>
                </div>
                <div>
                  <p className="text-sm text-slate-500">Recommended questions</p>
                  <p className="text-2xl font-bold">{plan.recommendedQuestionCount}</p>
                </div>
                <div>
                  <p className="text-sm text-slate-500">Estimated marks coverage</p>
                  <p className="text-2xl font-bold">{plan.estimatedMarksCoverage != null ? plan.estimatedMarksCoverage : 'N/A'}</p>
                </div>
              </div>
              <p className="mt-4 text-xs italic text-slate-500">{plan.disclaimer}</p>
            </div>

            <div className="card mb-6">
              <h2 className="mb-3 text-lg font-semibold">Topic priority</h2>
              <div className="space-y-2">
                {plan.topicPriorities.map((t) => (
                  <div key={t.topic} className="flex items-center justify-between rounded-lg border border-slate-200 p-3">
                    <div>
                      <p className="font-medium text-slate-900">{t.topic}</p>
                      <p className="text-sm text-slate-500">{t.reason}</p>
                    </div>
                    {priorityBadge(t.priority)}
                  </div>
                ))}
              </div>
            </div>

            {plan.uncoveredTopics.length > 0 && (
              <div className="card mb-6 border-l-4 border-amber-400">
                <h2 className="mb-2 text-lg font-semibold">Coverage gaps</h2>
                <p className="mb-2 text-xs text-slate-500">
                  No approved paper in the system yet has a verified question for these topics. Where time allowed,
                  we still added a 📘 Syllabus Coverage practice question above so you don't skip the topic entirely -
                  but treat it as a study aid, not a real past-exam question.
                </p>
                {plan.uncoveredTopics.map((t) => (
                  <p key={t.topic} className="text-sm text-slate-600">
                    <span className="font-medium">{t.topic}:</span> {t.reason}
                  </p>
                ))}
              </div>
            )}

            {plan.recommendedQuestions.length === 0 ? (
              <div className="card mb-6">
                <h2 className="mb-3 text-lg font-semibold">Your exam strategy</h2>
                <p className="text-slate-500">
                  No recommendations could be generated - there isn't enough verified question data yet for the
                  selected topics.
                </p>
              </div>
            ) : (
              <>
                <div className="card mb-6">
                  <h2 className="mb-3 text-lg font-semibold">🎯 Your exam strategy</h2>
                  <div className="grid gap-4 sm:grid-cols-3">
                    {TIERS.map((tier) => {
                      const count = groupByTier(plan.recommendedQuestions)[tier.key].length;
                      return (
                        <div key={tier.key} className={`rounded-xl border-l-4 p-4 ${tier.accent}`}>
                          <p className="font-semibold text-slate-900">{tier.emoji} {tier.label}</p>
                          <p className="text-2xl font-bold text-slate-900">{count}</p>
                          <p className="text-xs text-slate-500">question{count === 1 ? '' : 's'}</p>
                        </div>
                      );
                    })}
                  </div>
                  <p className="mt-3 text-xs text-slate-500">
                    Total recommended preparation: {plan.recommendedQuestions.length} question
                    {plan.recommendedQuestions.length === 1 ? '' : 's'} in ~{plan.estimatedStudyMinutes} minutes.
                  </p>
                </div>

                {TIERS.map((tier) => {
                  const questions = groupByTier(plan.recommendedQuestions)[tier.key];
                  if (questions.length === 0) return null;
                  return (
                    <div key={tier.key} className="card mb-6">
                      <h2 className="mb-3 text-lg font-semibold">
                        {tier.emoji} {tier.label} <span className="text-sm font-normal text-slate-500">({questions.length})</span>
                      </h2>
                      <div className="space-y-3">
                        {questions.map((q, idx) => (
                          <div key={q.questionId ?? `${tier.key}-${idx}`} className="rounded-xl border border-slate-200 p-4">
                            <p className="text-sm text-slate-500">
                              Topic: {q.topic} {q.marks != null && `· ${q.marks} marks`} · ~{q.estimatedMinutes} min
                            </p>
                            <p className="mt-1 text-slate-900">{q.questionText}</p>
                            <div className="mt-2 flex flex-wrap items-center gap-2">
                              {q.source === 'AI_GENERATED' ? (
                                <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-700">
                                  AI-generated · no past-paper match
                                </span>
                              ) : (
                                <>
                                  <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-700">
                                    {q.priorityCategory}
                                  </span>
                                  <span className="text-xs text-slate-500">Confidence score: {q.priorityScore}/100</span>
                                </>
                              )}
                            </div>
                            <p className="mt-2 text-xs italic text-slate-500">{q.reason}</p>
                          </div>
                        ))}
                      </div>
                    </div>
                  );
                })}

                <div className="card mb-6">
                  <h2 className="mb-3 text-lg font-semibold">Marks-wise breakdown</h2>
                  <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-4">
                    {groupByMarks(plan.recommendedQuestions).map(([marks, questions]) => (
                      <div key={marks} className="rounded-lg border border-slate-200 p-3">
                        <p className="text-sm text-slate-500">{marks === 'Unspecified' ? 'Marks unspecified' : `${marks} marks`}</p>
                        <p className="text-xl font-bold text-slate-900">{questions.length}</p>
                      </div>
                    ))}
                  </div>
                </div>
              </>
            )}

            {plan.studyPlan.length > 0 && (
              <div className="card">
                <h2 className="mb-3 text-lg font-semibold">Study plan</h2>
                <div className="space-y-3">
                  {plan.studyPlan.map((block) => (
                    <div key={block.label} className="rounded-lg border border-slate-200 p-3">
                      <p className="font-semibold text-slate-900">
                        {block.label} · {block.minutes} min {block.topic && `· ${block.topic}`}
                      </p>
                      <p className="text-sm text-slate-500">{block.note}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
