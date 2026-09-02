import { useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';

const API_BASE_URL = 'http://localhost:8080/api';

export default function LoginPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Admin OTP step: once otpRequired comes back from /auth/login, we stop
  // showing the password form and show a code-entry form instead. Nothing
  // here changes the STUDENT/FACULTY path, which never sets otpStage.
  const [otpStage, setOtpStage] = useState(false);
  const [otpMessage, setOtpMessage] = useState('');
  const [otpCode, setOtpCode] = useState('');

  const applySessionAndRoute = (data) => {
    localStorage.setItem('token', data.token);
    localStorage.setItem('role', data.role);
    localStorage.setItem('username', data.username);
    localStorage.setItem('fullName', data.fullName || data.username);

    if (data.role === 'STUDENT') {
      navigate('/student');
    } else if (data.role === 'FACULTY') {
      navigate('/faculty');
    } else if (data.role === 'ADMIN') {
      navigate('/admin');
    }
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await axios.post(`${API_BASE_URL}/auth/login`, {
        username,
        password
      });

      const { data } = response.data;

      if (data.otpRequired) {
        setOtpStage(true);
        setOtpMessage(data.message || 'A verification code has been sent to your registered email.');
      } else {
        applySessionAndRoute(data);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Login failed. Please check your credentials.');
    }
    setLoading(false);
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await axios.post(`${API_BASE_URL}/auth/verify-otp`, {
        username,
        code: otpCode
      });

      applySessionAndRoute(response.data.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Verification failed. Please check the code and try again.');
    }
    setLoading(false);
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 p-6">
      <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 shadow-xl">
        <div className="mb-8 text-center">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-blue-600">EXAMIQ</p>
          <h1 className="mt-3 text-3xl font-bold text-slate-900">Welcome back</h1>
          <p className="mt-2 text-sm text-slate-500">Sign in to access your academic workspace</p>
        </div>

        {error && (
          <div className="mb-6 rounded-lg bg-red-50 p-4 text-sm text-red-800">
            {error}
          </div>
        )}

        {!otpStage ? (
          <>
            <form onSubmit={handleLogin} className="space-y-5">
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">Username</label>
                <input
                  className="w-full rounded-lg border border-slate-300 px-3 py-2.5 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                  placeholder="Enter your username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                />
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-700">Password</label>
                <input
                  type="password"
                  className="w-full rounded-lg border border-slate-300 px-3 py-2.5 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>
              <button
                type="submit"
                disabled={loading}
                className="w-full btn-primary bg-blue-600 hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? 'Signing in...' : 'Sign in'}
              </button>
            </form>

            <div className="mt-6 text-center">
              <p className="text-sm text-slate-600">
                New here?{' '}
                <a href="/register" className="font-medium text-blue-600 hover:underline">
                  Create an account
                </a>
              </p>
            </div>
          </>
        ) : (
          <form onSubmit={handleVerifyOtp} className="space-y-5">
            <div className="rounded-lg bg-blue-50 p-4 text-sm text-blue-800">
              {otpMessage}
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium text-slate-700">Verification code</label>
              <input
                className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-center text-lg tracking-[0.5em] outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                placeholder="000000"
                inputMode="numeric"
                maxLength={6}
                value={otpCode}
                onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                autoFocus
                required
              />
            </div>
            <button
              type="submit"
              disabled={loading || otpCode.length !== 6}
              className="w-full btn-primary bg-blue-600 hover:bg-blue-700 disabled:opacity-50"
            >
              {loading ? 'Verifying...' : 'Verify code'}
            </button>
            <button
              type="button"
              onClick={() => {
                setOtpStage(false);
                setOtpCode('');
                setError('');
              }}
              className="w-full text-center text-sm text-slate-500 hover:text-slate-700"
            >
              Back to login
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
