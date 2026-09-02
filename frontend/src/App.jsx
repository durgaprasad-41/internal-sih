import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import StudentDashboard from './pages/StudentDashboard';
import FacultyDashboard from './pages/FacultyDashboard';
import AdminDashboard from './pages/AdminDashboard';
import AdminPendingApprovalsPage from './pages/AdminPendingApprovalsPage';
import AdminUnderReviewPage from './pages/AdminUnderReviewPage';
import AdminNotificationsPage from './pages/AdminNotificationsPage';
import FacultyReviewsPage from './pages/FacultyReviewsPage';
import SearchPage from './pages/SearchPage';
import UploadPage from './pages/UploadPage';
import PaperDetailPage from './pages/PaperDetailPage';
import ProfilePage from './pages/ProfilePage';
import BookmarksPage from './pages/BookmarksPage';
import MyUploadsPage from './pages/MyUploadsPage';
import NotificationsPage from './pages/NotificationsPage';
import SmartRevisionPage from './pages/SmartRevisionPage';
import GenerateQuestionPaperPage from './pages/GenerateQuestionPaperPage';
import MyGeneratedPapersPage from './pages/MyGeneratedPapersPage';
import QuestionPaperDetailPage from './pages/QuestionPaperDetailPage';
import RequireAdmin from './components/RequireAdmin';
import RequireFaculty from './components/RequireFaculty';

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/student" element={<StudentDashboard />} />
      <Route path="/student/profile" element={<ProfilePage />} />
      <Route path="/student/bookmarks" element={<BookmarksPage />} />
      <Route path="/student/uploads" element={<MyUploadsPage />} />
      <Route path="/student/notifications" element={<NotificationsPage />} />
      <Route path="/student/smart-revision" element={<SmartRevisionPage />} />
      <Route
        path="/faculty"
        element={
          <RequireFaculty>
            <FacultyDashboard />
          </RequireFaculty>
        }
      />
      <Route
        path="/faculty/uploads"
        element={
          <RequireFaculty>
            <MyUploadsPage />
          </RequireFaculty>
        }
      />
      <Route
        path="/faculty/reviews"
        element={
          <RequireFaculty>
            <FacultyReviewsPage />
          </RequireFaculty>
        }
      />
      <Route
        path="/faculty/question-papers/generate"
        element={
          <RequireFaculty>
            <GenerateQuestionPaperPage />
          </RequireFaculty>
        }
      />
      <Route
        path="/faculty/question-papers"
        element={
          <RequireFaculty>
            <MyGeneratedPapersPage />
          </RequireFaculty>
        }
      />
      <Route
        path="/faculty/question-papers/:id"
        element={
          <RequireFaculty>
            <QuestionPaperDetailPage />
          </RequireFaculty>
        }
      />
      <Route
        path="/admin"
        element={
          <RequireAdmin>
            <AdminDashboard />
          </RequireAdmin>
        }
      />
      <Route
        path="/admin/dashboard"
        element={
          <RequireAdmin>
            <AdminDashboard />
          </RequireAdmin>
        }
      />
      <Route
        path="/admin/pending-approvals"
        element={
          <RequireAdmin>
            <AdminPendingApprovalsPage />
          </RequireAdmin>
        }
      />
      <Route
        path="/admin/under-review"
        element={
          <RequireAdmin>
            <AdminUnderReviewPage />
          </RequireAdmin>
        }
      />
      <Route
        path="/admin/notifications"
        element={
          <RequireAdmin>
            <AdminNotificationsPage />
          </RequireAdmin>
        }
      />
      <Route path="/search" element={<SearchPage />} />
      <Route path="/upload" element={<UploadPage />} />
      <Route path="/paper/:id" element={<PaperDetailPage />} />
    </Routes>
  );
}

export default App;
