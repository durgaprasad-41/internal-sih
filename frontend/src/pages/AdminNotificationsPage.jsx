import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import AdminHeader from '../components/AdminHeader';

const API_BASE_URL = 'http://localhost:8080/api';

export default function AdminNotificationsPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const authHeaders = () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` });

  const fetchNotifications = async () => {
    setLoading(true);
    try {
      const response = await axios.get(`${API_BASE_URL}/notifications`, { headers: authHeaders() });
      setItems(response.data.data || []);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load notifications.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNotifications();
  }, []);

  const markRead = async (id) => {
    try {
      await axios.put(`${API_BASE_URL}/notifications/${id}/read`, {}, { headers: authHeaders() });
      fetchNotifications();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to mark notification as read.');
    }
  };

  const handleNotificationClick = async (notification) => {
    if (!notification.isRead) {
      await markRead(notification.id);
    }
    if (notification.relatedPaperId) {
      navigate('/admin/pending-approvals');
    }
  };

  return (
    <div className="min-h-screen bg-slate-100 p-6">
      <div className="mx-auto max-w-7xl">
        <AdminHeader />

        <div className="card">
          <h2 className="mb-6 text-xl font-semibold">Notifications</h2>

          {error && <div className="mb-4 rounded-lg bg-red-50 p-4 text-sm text-red-800">{error}</div>}

          {loading ? (
            <p className="text-slate-500">Loading...</p>
          ) : items.length === 0 ? (
            <p className="text-slate-500">No notifications yet.</p>
          ) : (
            <div className="space-y-3">
              {items.map((n) => (
                <div
                  key={n.id}
                  className={`rounded-xl border p-4 ${n.isRead ? 'bg-slate-50' : 'bg-blue-50'} ${
                    n.relatedPaperId ? 'cursor-pointer hover:border-blue-300' : ''
                  }`}
                  onClick={() => handleNotificationClick(n)}
                >
                  <div className="flex justify-between gap-3">
                    <div>
                      <h3 className="font-semibold">{n.title}</h3>
                      <p className="text-sm text-slate-600">{n.message}</p>
                    </div>
                    {!n.isRead && (
                      <button
                        className="btn-secondary shrink-0"
                        onClick={(e) => {
                          e.stopPropagation();
                          markRead(n.id);
                        }}
                      >
                        Mark read
                      </button>
                    )}
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
