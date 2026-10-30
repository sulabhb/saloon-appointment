import { Outlet } from 'react-router'
import { useMe } from '../../api/queries'
import { EmptyState } from '../../components/ui'

/** The server enforces this too; the check here only avoids showing pages that would fail. */
export function RequirePlatformAdmin() {
  const me = useMe()
  if (!me.data?.user.platformAdmin) {
    return <EmptyState title="Platform team only">This area is for the team that runs the platform.</EmptyState>
  }
  return <Outlet />
}
