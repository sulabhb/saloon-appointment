import { Link } from 'react-router'
import { useBusinesses } from '../../api/queries'
import { Alert, EmptyState, PageHeader, Spinner, StatusBadge, formatDate } from '../../components/ui'

/** Platform dashboard (TEN-10): every business with its status and size. */
export function BusinessListPage() {
  const businesses = useBusinesses()

  return (
    <>
      <PageHeader
        title="Businesses"
        description="Salons on the platform. Only Live businesses take bookings."
        actions={
          <Link
            to="/platform/businesses/new"
            className="inline-flex min-h-11 items-center rounded-full bg-accent px-5 text-sm font-medium text-on-accent hover:bg-accent-strong"
          >
            New business
          </Link>
        }
      />
      {businesses.isPending && <Spinner label="Loading businesses" />}
      {businesses.error && <Alert>{businesses.error.message}</Alert>}
      {businesses.data?.length === 0 && (
        <EmptyState title="No businesses yet">Create the first one and send its owner the invite link.</EmptyState>
      )}
      {businesses.data && businesses.data.length > 0 && (
        <div className="relative overflow-x-auto rounded-card border border-line bg-surface">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="border-b border-line text-muted">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">Salon</th>
                <th scope="col" className="px-4 py-3 font-medium">Status</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Branches</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Staff</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Open invites</th>
                <th scope="col" className="px-4 py-3 font-medium">Created</th>
              </tr>
            </thead>
            <tbody>
              {businesses.data.map((b) => (
                <tr key={b.id} className="border-b border-line last:border-0 hover:bg-sunken/60">
                  <td className="px-4 py-3">
                    <Link to={`/platform/businesses/${b.id}`} className="font-medium hover:underline">
                      {b.displayName}
                    </Link>
                    <div className="whitespace-nowrap font-mono text-xs text-muted">{b.slug}</div>
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={b.status} />
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums">{b.activeBranches}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{b.activeStaff}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{b.pendingInvites}</td>
                  <td className="px-4 py-3 text-muted">{formatDate(b.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
