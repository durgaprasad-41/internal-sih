import { Navigate } from 'react-router-dom';

/**
 * Frontend-level guard for admin-only routes. This is a defense-in-depth
 * convenience layer, not the source of truth for authorization - every
 * admin API call is still independently enforced by Spring Security
 * (hasRole('ADMIN')) regardless of what this component does.
 */
export default function RequireAdmin({ children }) {
  const token = localStorage.getItem('token');
  const role = localStorage.getItem('role');

  if (!token || role !== 'ADMIN') {
    return <Navigate to="/login" replace />;
  }

  return children;
}
