import { Link, useNavigate, useLocation } from 'react-router-dom';

const NAV_LINKS = [
  { to: '/faculty', label: 'Dashboard' },
  { to: '/upload', label: 'Upload' },
  { to: '/faculty/uploads', label: 'My Uploads' },
  { to: '/faculty/reviews', label: 'Papers to Review' },
  { to: '/search', label: 'Search' },
  { to: '/faculty/question-papers/generate', label: 'Generate Question Paper' },
  { to: '/faculty/question-papers', label: 'My Generated Papers' }
];

export default function FacultyHeader() {
  const navigate = useNavigate();
  const location = useLocation();

  const facultyName = localStorage.getItem('fullName') || localStorage.getItem('username');

  const isActive = (path) => location.pathname === path;

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('role');
    localStorage.removeItem('username');
    localStorage.removeItem('fullName');
    navigate('/login');
  };

  return (
    <header className="mb-8 rounded-2xl bg-slate-900 px-6 py-4 text-white">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-6">
          <div>
            <p className="text-xs uppercase tracking-[0.25em] text-blue-300">EXAMIQ</p>
            <h1 className="mt-1 text-xl font-bold">Faculty Workspace</h1>
          </div>
          <nav className="flex flex-wrap gap-1">
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

        <div className="flex items-center gap-4">
          <div className="text-right">
            <p className="text-sm font-semibold">{facultyName}</p>
            <p className="text-xs font-medium uppercase tracking-wider text-blue-300">FACULTY</p>
          </div>
          <button className="btn-secondary" onClick={handleLogout}>Logout</button>
        </div>
      </div>
    </header>
  );
}
