import {
  Building2,
  CalendarClock,
  CalendarDays,
  CalendarRange,
  LayoutDashboard,
  LogOut,
  Menu,
  Scissors,
  ShieldCheck,
  Store,
  Users,
  X,
  type LucideIcon,
} from 'lucide-react'
import { useState } from 'react'
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useMe, useSelectBusiness, useSignOut } from '../api/queries'
import type { Me, StaffRole } from '../api/types'
import { Avatar, Spinner, roleLabel } from '../components/ui'

/** Pages for signed-in people. Signed-out visitors go to the sign-in page and come back after. */
export function RequireSignIn() {
  const me = useMe()
  const location = useLocation()
  if (me.isPending) {
    return (
      <div className="grid min-h-dvh place-items-center">
        <Spinner label="Loading" />
      </div>
    )
  }
  if (!me.data) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }
  return <AppShell me={me.data} />
}

const isSetupRole = (role: StaffRole | undefined) => role === 'OWNER' || role === 'MANAGER'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

function navigationFor(me: Me): { title?: string; items: NavItem[] }[] {
  const groups: { title?: string; items: NavItem[] }[] = []
  const role = me.selected?.role
  if (me.selected) {
    const items: NavItem[] = [
      { to: '/home', label: 'Today', icon: CalendarDays },
      { to: '/calendar', label: 'Calendar', icon: CalendarClock },
    ]
    if (isSetupRole(role)) {
      items.push(
        { to: '/setup', label: 'Overview', icon: LayoutDashboard },
        { to: '/branches', label: 'Branches', icon: Store },
        { to: '/services', label: 'Services', icon: Scissors },
        { to: '/staff', label: 'Staff', icon: Users },
        { to: '/schedule', label: 'Schedule', icon: CalendarRange },
      )
    }
    if (role === 'OWNER') items.push({ to: '/team', label: 'Team & access', icon: ShieldCheck })
    groups.push({ items })
  }
  if (me.user.platformAdmin) {
    groups.push({ title: 'Platform', items: [{ to: '/platform', label: 'Businesses', icon: Building2 }] })
  }
  return groups
}

function AppShell({ me }: { me: Me }) {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className="min-h-dvh lg:pl-64">
      {/* Phone and tablet top bar */}
      <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-line bg-surface/90 px-4 backdrop-blur lg:hidden">
        <button
          type="button"
          onClick={() => setMenuOpen(true)}
          aria-label="Open menu"
          className="-ml-1 rounded-lg p-1.5 text-muted hover:bg-sunken hover:text-ink"
        >
          <Menu className="size-5" aria-hidden />
        </button>
        <Brand />
      </header>

      {/* Sidebar: fixed on large screens, slide-in drawer below */}
      <div
        className={`fixed inset-0 z-40 bg-black/40 lg:hidden ${menuOpen ? 'block animate-fade-in' : 'hidden'}`}
        onClick={() => setMenuOpen(false)}
        aria-hidden
      />
      <aside
        aria-label="Sidebar"
        className={`fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-line bg-surface transition-transform lg:translate-x-0 ${
          menuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-14 items-center justify-between px-5 lg:h-16">
          <Brand />
          <button
            type="button"
            onClick={() => setMenuOpen(false)}
            aria-label="Close menu"
            className="rounded-lg p-1.5 text-muted hover:bg-sunken lg:hidden"
          >
            <X className="size-5" aria-hidden />
          </button>
        </div>
        <BusinessSwitcher me={me} />
        <nav aria-label="Main" className="flex-1 overflow-y-auto px-3 py-2">
          {navigationFor(me).map((group, i) => (
            <div key={group.title ?? i} className="mb-4">
              {group.title && (
                <p className="mb-1 px-3 text-xs font-medium text-subtle">{group.title}</p>
              )}
              <ul className="flex flex-col gap-0.5">
                {group.items.map((item) => (
                  <li key={item.to}>
                    <NavLink
                      to={item.to}
                      // Close the phone menu once a destination is chosen.
                      onClick={() => setMenuOpen(false)}
                      className={({ isActive }) =>
                        `flex h-9 items-center gap-3 rounded-lg px-3 text-sm transition-colors ${
                          isActive ? 'bg-sunken font-medium text-ink' : 'text-muted hover:bg-sunken/60 hover:text-ink'
                        }`
                      }
                    >
                      <item.icon className="size-4" aria-hidden />
                      {item.label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
        <AccountFooter me={me} />
      </aside>

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-8 lg:py-10">
        <Outlet />
      </main>
    </div>
  )
}

function Brand() {
  return (
    <span className="flex items-center gap-2 font-semibold">
      <span className="flex size-7 items-center justify-center rounded-lg bg-accent text-on-accent">
        <Scissors className="size-4" aria-hidden />
      </span>
      Salon Console
    </span>
  )
}

/** Shows the current business; people in several businesses can switch here (D-18). */
function BusinessSwitcher({ me }: { me: Me }) {
  const select = useSelectBusiness()
  const navigate = useNavigate()
  if (me.businesses.length === 0) return null
  const current = me.businesses.find((b) => b.businessId === me.selected?.businessId)

  return (
    <div className="px-3 pb-2">
      {me.businesses.length === 1 && current ? (
        <div className="rounded-lg border border-line px-3 py-2">
          <p className="truncate text-sm font-medium">{current.displayName}</p>
          <p className="text-xs text-muted">{roleLabel[current.role]}</p>
        </div>
      ) : (
        <label className="block">
          <span className="sr-only">Business</span>
          <select
            value={me.selected?.businessId ?? ''}
            disabled={select.isPending}
            onChange={(e) => select.mutate(e.target.value, { onSuccess: () => navigate('/') })}
            className="h-10 w-full rounded-lg border border-line bg-surface px-3 text-sm font-medium"
          >
            {!me.selected && (
              <option value="" disabled>
                Choose a business
              </option>
            )}
            {me.businesses.map((b) => (
              <option key={b.businessId} value={b.businessId}>
                {b.displayName} · {roleLabel[b.role]}
              </option>
            ))}
          </select>
        </label>
      )}
    </div>
  )
}

function AccountFooter({ me }: { me: Me }) {
  const signOut = useSignOut()
  const navigate = useNavigate()
  return (
    <div className="flex items-center gap-3 border-t border-line px-4 py-3">
      <Avatar name={me.user.fullName} size="sm" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{me.user.fullName}</p>
        <p className="truncate text-xs text-muted">{me.user.email}</p>
      </div>
      <button
        type="button"
        aria-label="Sign out"
        title="Sign out"
        disabled={signOut.isPending}
        onClick={() => signOut.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) })}
        className="rounded-lg p-1.5 text-muted hover:bg-sunken hover:text-ink"
      >
        <LogOut className="size-4" aria-hidden />
      </button>
    </div>
  )
}

/** Sends people to the right starting page for their account. */
export function HomeRedirect() {
  const me = useMe()
  if (!me.data) return null
  if (me.data.selected) return <Navigate to={isSetupRole(me.data.selected.role) ? '/setup' : '/home'} replace />
  if (me.data.businesses.length > 0) return <Navigate to="/home" replace />
  if (me.data.user.platformAdmin) return <Navigate to="/platform" replace />
  return <Navigate to="/login" replace />
}

/** Setup pages are for owners and managers; the server enforces this too. */
export function RequireSetupRole() {
  const me = useMe()
  if (!isSetupRole(me.data?.selected?.role)) return <Navigate to="/" replace />
  return <Outlet />
}
