import { Navigate } from 'react-router-dom';

/**
 * Frontend-level guard for faculty-only routes, mirroring RequireAdmin.
 * Defense-in-depth only - every faculty API call is still independently
 * enforced by Spring Security (hasRole('FACULTY')).
 */
export default function RequireFaculty({ children }) {
  const token = localStorage.getItem('token');
  const role = localStorage.getItem('role');

  if (!token || role !== 'FACULTY') {
    return <Navigate to="/login" replace />;
  }

  return children;
}
