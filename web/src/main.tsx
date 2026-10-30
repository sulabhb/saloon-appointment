import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider, createBrowserRouter } from 'react-router'
import { ApiError } from './api/client'
import { ToastProvider } from './components/Toast'
import { HomeRedirect, RequireSetupRole, RequireSignIn } from './layout/AppShell'
import { InvitePage } from './pages/InvitePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { BusinessDetailPage } from './pages/platform/BusinessDetailPage'
import { BusinessListPage } from './pages/platform/BusinessListPage'
import { NewBusinessPage } from './pages/platform/NewBusinessPage'
import { RequirePlatformAdmin } from './pages/platform/RequirePlatformAdmin'
import { BranchDetailPage } from './pages/setup/BranchDetailPage'
import { BranchesPage } from './pages/setup/BranchesPage'
import { OverviewPage } from './pages/setup/OverviewPage'
import { ServicesPage } from './pages/setup/ServicesPage'
import { BookingPage } from './booking/BookingPage'
import { FindBookingPage } from './booking/FindBookingPage'
import { ManagePage } from './booking/ManagePage'
import { CalendarPage } from './pages/calendar/CalendarPage'
import { ForgotPasswordPage, ResetPasswordPage } from './pages/PasswordResetPages'
import { SchedulePage } from './pages/schedule/SchedulePage'
import { StaffPage } from './pages/setup/StaffPage'
import { TeamPage } from './pages/setup/TeamPage'
import { StaffHomePage } from './pages/staff/StaffHomePage'
import './styles.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Client errors (401, 403, 404) will not fix themselves on retry.
      retry: (failures, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failures < 2,
      refetchOnWindowFocus: true,
    },
  },
})

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password/:token', element: <ResetPasswordPage /> },
  // Public booking site: no sign-in, the salon comes from the link (BKG-1, MGT-1).
  { path: '/book/:business', element: <BookingPage /> },
  { path: '/book/:business/find', element: <FindBookingPage /> },
  { path: '/book/:business/:branch', element: <BookingPage /> },
  { path: '/m/:token', element: <ManagePage /> },
  { path: '/invite/:token', element: <InvitePage /> },
  {
    element: <RequireSignIn />,
    children: [
      { index: true, element: <HomeRedirect /> },
      { path: 'home', element: <StaffHomePage /> },
      { path: 'calendar', element: <CalendarPage /> },
      {
        element: <RequireSetupRole />,
        children: [
          { path: 'setup', element: <OverviewPage /> },
          { path: 'branches', element: <BranchesPage /> },
          { path: 'branches/:branchId', element: <BranchDetailPage /> },
          { path: 'services', element: <ServicesPage /> },
          { path: 'staff', element: <StaffPage /> },
          { path: 'schedule', element: <SchedulePage /> },
          { path: 'team', element: <TeamPage /> },
        ],
      },
      {
        path: 'platform',
        element: <RequirePlatformAdmin />,
        children: [
          { index: true, element: <BusinessListPage /> },
          { path: 'businesses/new', element: <NewBusinessPage /> },
          { path: 'businesses/:businessId', element: <BusinessDetailPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>
  </StrictMode>,
)
