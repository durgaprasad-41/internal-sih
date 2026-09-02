import { useEffect, useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

const NAV_LINKS = [
  { to: '/admin/dashboard', label: 'Dashboard' },
  { to: '/admin/pending-approvals', label: 'Pending Approvals' },
  { to: '/admin/under-review', label: 'Under Review' },
  { to: '/admin/notifications', label: 'Notifications' }
];

export default function AdminHeader() {
  const navigate = useNavigate();
  const location = useLocation();
  const [unreadCount, setUnreadCount] = useState(null);

  const adminName = localStorage.getItem('fullName') || localStorage.getItem('username');

  const loadUnreadCount = async () => {
    try {
      const response = await axios.get(`${API_BASE_URL}/notifications/unread`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
      });
      setUnreadCount((response.data.data || []).length);
    } catch (err) {
      // Non-fatal: the bell just shows no count if this fails.
    }
  };

  useEffect(() => {
    loadUnreadCount();
  }, [location.pathname]);

  const isActive = (path) => location.pathname === path;

  return (
    <header className="mb-8 rounded-2xl bg-slate-900 px-6 py-4 text-white">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-8">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] text-blue-300">EXAMIQ</p>
            <h1 className="mt-1 text-xl font-bold">Administrator Dashboard</h1>
          </div>
          <nav className="flex gap-1">
            {NAV_LINKS.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className={`rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive(link.to) ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800'
                }`}
              >
                {link.label}
              </Link>
            ))}
          </nav>
        </div>

        <div className="flex items-center gap-5">
          <button
            className="relative rounded-full p-2 text-xl hover:bg-slate-800"
            onClick={() => navigate('/admin/notifications')}
            aria-label="Notifications"
            title="Notifications"
          >
            🔔
            {unreadCount !== null && unreadCount > 0 && (
              <span className="absolute -right-1 -top-1 flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-red-500 px-1 text-xs font-bold text-white">
                {unreadCount}
              </span>
            )}
          </button>
          <div className="text-right">
            <p className="text-sm font-semibold">{adminName}</p>
            <p className="text-xs font-medium uppercase tracking-wider text-blue-300">ADMIN</p>
          </div>
        </div>
      </div>
    </header>
  );
}
