import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [papers, setPapers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [myScores, setMyScores] = useState({});
  const [submittingRatingId, setSubmittingRatingId] = useState(null);
  const [ratingMessages, setRatingMessages] = useState({});

  // nextQuery must default to a string, not the event object React's onClick
  // passes when a handler is wired directly as onClick={handleSearch}.
  const handleSearch = async (nextQuery) => {
    const value = typeof nextQuery === 'string' ? nextQuery : query;
    const trimmed = value.trim();
    if (!trimmed) {
      setPapers([]);
      setHasSearched(false);
      setSearchError('');
      setSearchParams({});
      return;
    }

    setLoading(true);
    setSearchError('');
    try {
      setSearchParams({ q: trimmed });
      console.log('[SearchPage] GET /papers/search', { q: trimmed });
      const response = await axios.get(`${API_BASE_URL}/papers/search`, {
        params: { q: trimmed },
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
      });
      console.log('[SearchPage] search response', response.data);
      setPapers(response.data.data || []);
    } catch (error) {
      console.error('[SearchPage] search request failed', error);
      setPapers([]);
      setSearchError(error.response?.data?.message || 'Search failed. Please try again.');
    }
    setHasSearched(true);
    setLoading(false);
  };

  useEffect(() => {
    const urlQuery = searchParams.get('q') || '';
    if (urlQuery) {
      setQuery(urlQuery);
      handleSearch(urlQuery);
    }
  }, [searchParams]);

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch(query);
    }
  };

  const submitRating = async (paperId) => {
    const score = myScores[paperId];
    if (!score) {
      setRatingMessages((prev) => ({ ...prev, [paperId]: 'Select a star rating first.' }));
      return;
    }
    setSubmittingRatingId(paperId);
    try {
      await axios.post(`${API_BASE_URL}/papers/${paperId}/rate`, null, {
        params: { score },
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
      });
      const { data } = await axios.get(`${API_BASE_URL}/papers/${paperId}/average-rating`);
      setPapers((prev) => prev.map((p) => (p.id === paperId ? { ...p, averageRating: data.data } : p)));
      setRatingMessages((prev) => ({ ...prev, [paperId]: 'Thanks for rating!' }));
    } catch (err) {
      setRatingMessages((prev) => ({
        ...prev,
        [paperId]: err.response?.data?.message || 'Unable to submit your rating.'
      }));
    } finally {
      setSubmittingRatingId(null);
    }
  };

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-6xl">
        <div className="mb-8 rounded-2xl bg-gradient-to-br from-blue-600 to-indigo-700 px-8 py-12 text-white">
          <h1 className="text-4xl font-bold">Smart Paper Search</h1>
          <p className="mt-2 text-lg opacity-90">Find previous exam papers, questions, and study materials across universities</p>
        </div>

        <div className="card mb-8">
          <div className="flex flex-col gap-4">
            <input
              className="rounded-lg border border-slate-300 px-4 py-3"
              placeholder="Search by subject, topic, university, exam type, or keywords..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyPress={handleKeyPress}
            />
            <div className="flex gap-4">
              <button className="btn-primary flex-1" onClick={() => handleSearch(query)} disabled={loading}>
                {loading ? 'Searching...' : 'Search'}
              </button>
              <button className="btn-secondary" onClick={() => {
                setQuery('');
                setPapers([]);
                setHasSearched(false);
                setSearchError('');
              }}>
                Clear
              </button>
            </div>
          </div>
        </div>

        {papers.length > 0 && (
          <div className="space-y-4">
            <p className="text-sm text-slate-600">Found {papers.length} papers</p>
            {papers.map((paper) => (
              <div key={paper.id} className="card">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-lg font-semibold text-slate-900">{paper.title}</h3>
                    <div className="mt-2 flex flex-wrap gap-2">
                      <span className="inline-block rounded-full bg-blue-100 px-3 py-1 text-sm text-blue-700">{paper.subjectName}</span>
                      <span className="inline-block rounded-full bg-green-100 px-3 py-1 text-sm text-green-700">{paper.universityName}</span>
                      <span className="inline-block rounded-full bg-gray-100 px-3 py-1 text-sm text-gray-700">{paper.year}</span>
                    </div>
                    <p className="mt-2 text-sm text-slate-600">By {paper.author} • {paper.examType}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-yellow-600">★ {paper.averageRating?.toFixed(1) || 'N/A'}</p>
                    <button className="mt-3 btn-primary" onClick={() => navigate(`/paper/${paper.id}`)}>View paper</button>
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-3">
                  <span className="text-sm text-slate-500">Rate this paper:</span>
                  <div className="flex gap-1">
                    {[1, 2, 3, 4, 5].map((n) => (
                      <button
                        key={n}
                        type="button"
                        onClick={() => setMyScores((prev) => ({ ...prev, [paper.id]: n }))}
                        className={`text-xl leading-none ${
                          n <= (myScores[paper.id] || 0) ? 'text-yellow-500' : 'text-slate-300'
                        }`}
                        aria-label={`Rate ${n} star${n > 1 ? 's' : ''}`}
                      >
                        ★
                      </button>
                    ))}
                  </div>
                  <button
                    className="btn-secondary"
                    disabled={submittingRatingId === paper.id}
                    onClick={() => submitRating(paper.id)}
                  >
                    {submittingRatingId === paper.id ? 'Submitting...' : 'Submit rating'}
                  </button>
                  {ratingMessages[paper.id] && (
                    <span className="text-sm text-slate-500">{ratingMessages[paper.id]}</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {!loading && hasSearched && searchError && (
          <div className="card text-center text-red-600">
            <p>{searchError}</p>
          </div>
        )}

        {!loading && hasSearched && !searchError && papers.length === 0 && (
          <div className="card text-center">
            <p className="text-slate-600">No papers found for "{query}"</p>
          </div>
        )}
      </div>
    </div>
  );
}
