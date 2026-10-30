import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useBusiness, useChangeStatus, useInviteOwner, useRevokeInvite } from '../../api/queries'
import type { BusinessStatus, BusinessSummary, InviteView, IssuedInvite } from '../../api/types'
import { Alert, Button, Card, ConfirmButton, CopyField, EmptyState, PageHeader, Spinner, StatusBadge, TextField, formatDate, roleLabel } from '../../components/ui'

export function BusinessDetailPage() {
  const { businessId = '' } = useParams()
  const detail = useBusiness(businessId)

  if (detail.isPending) return <Spinner label="Loading business" />
  if (detail.error) return <Alert>{detail.error.message}</Alert>

  const { business, invites } = detail.data
  return (
    <>
      <p className="mb-2 text-sm">
        <Link to="/platform" className="text-muted hover:underline">
          ← Businesses
        </Link>
      </p>
      <PageHeader
        title={business.displayName}
        description={
          <span className="inline-flex items-center gap-2">
            <StatusBadge status={business.status} /> <span className="font-mono text-sm">{business.slug}</span>
          </span>
        }
      />
      {/* min-w-0: grid items otherwise grow to their widest content (the invite table) and push the page sideways. */}
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="flex min-w-0 flex-col gap-6 lg:col-span-2">
          <Card>
            <h2 className="mb-4 font-semibold">Invites</h2>
            {invites.length === 0 ? (
              <EmptyState title="No invites" />
            ) : (
              <InviteTable businessId={business.id} invites={invites} />
            )}
          </Card>
          {business.status !== 'CLOSED' && <OwnerInviteForm businessId={business.id} />}
        </div>
        <div className="flex min-w-0 flex-col gap-6">
          <Card>
            <h2 className="mb-4 font-semibold">At a glance</h2>
            <dl className="grid grid-cols-3 gap-3 text-center">
              <Stat label="Branches" value={business.activeBranches} />
              <Stat label="Staff" value={business.activeStaff} />
              <Stat label="Open invites" value={business.pendingInvites} />
            </dl>
            <p className="mt-4 text-sm text-muted">Created {formatDate(business.createdAt)}</p>
          </Card>
          <StatusCard business={business} />
        </div>
      </div>
    </>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl bg-sunken px-2 py-3">
      <dd className="text-2xl font-semibold tabular-nums">{value}</dd>
      <dt className="text-xs text-muted">{label}</dt>
    </div>
  )
}

function InviteTable({ businessId, invites }: { businessId: string; invites: InviteView[] }) {
  const revoke = useRevokeInvite(businessId)
  return (
    // relative: keeps absolutely positioned content (sr-only labels) inside the scroll box.
    <div className="relative overflow-x-auto">
      {revoke.error && <Alert>{revoke.error.message}</Alert>}
      <table className="w-full min-w-[520px] text-left text-sm">
        <thead className="text-muted">
          <tr>
            <th scope="col" className="py-2 pr-3 font-medium">Email</th>
            <th scope="col" className="py-2 pr-3 font-medium">Role</th>
            <th scope="col" className="py-2 pr-3 font-medium">Status</th>
            <th scope="col" className="py-2 pr-3 font-medium">Sent</th>
            <th scope="col" className="py-2"><span className="sr-only">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          {invites.map((i) => (
            <tr key={i.id} className="border-t border-line">
              <td className="py-2.5 pr-3">{i.email}</td>
              <td className="py-2.5 pr-3">{roleLabel[i.role]}</td>
              <td className="py-2.5 pr-3">
                <StatusBadge status={i.status} />
              </td>
              <td className="py-2.5 pr-3 text-muted">{formatDate(i.createdAt)}</td>
              <td className="py-2.5 text-right">
                {i.status === 'PENDING' && (
                  <ConfirmButton
                    label="Revoke"
                    question="Stop this link working?"
                    busy={revoke.isPending && revoke.variables === i.id}
                    onConfirm={() => revoke.mutate(i.id)}
                  />
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function OwnerInviteForm({ businessId }: { businessId: string }) {
  const invite = useInviteOwner(businessId)
  const [email, setEmail] = useState('')
  const [issued, setIssued] = useState<IssuedInvite | null>(null)

  function submit(event: FormEvent) {
    event.preventDefault()
    invite.mutate(email.trim(), {
      onSuccess: (result) => {
        setIssued(result)
        setEmail('')
      },
    })
  }

  return (
    <Card>
      <h2 className="font-semibold">Send a new owner invite</h2>
      <p className="mt-1 mb-4 text-sm text-muted">Replaces any open invite to the same email, for example when one expired.</p>
      <form onSubmit={submit} className="flex flex-col gap-4 sm:flex-row sm:items-end" noValidate>
        <TextField label="Owner email" type="email" className="flex-1" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Button type="submit" busy={invite.isPending} disabled={!email.includes('@')}>
          Create invite
        </Button>
      </form>
      {invite.error && <div className="mt-4"><Alert>{invite.error.message}</Alert></div>}
      {issued && (
        <div className="mt-4 flex flex-col gap-3">
          <CopyField label={`Invite link for ${issued.email}`} value={issued.url} />
          <p className="text-sm text-muted">Shown only now. Valid until {formatDate(issued.expiresAt)}.</p>
        </div>
      )}
    </Card>
  )
}

const actions: Record<BusinessStatus, { target: BusinessStatus; label: string; explain: string }[]> = {
  ONBOARDING: [{ target: 'LIVE', label: 'Set live', explain: 'Customers can find it and book.' }],
  LIVE: [{ target: 'SUSPENDED', label: 'Suspend', explain: 'Booking pauses; staff can still sign in.' }],
  SUSPENDED: [{ target: 'LIVE', label: 'Reactivate', explain: 'Booking resumes.' }],
  CLOSED: [],
}

function StatusCard({ business }: { business: BusinessSummary }) {
  const change = useChangeStatus(business.id)
  const [confirmClose, setConfirmClose] = useState(false)

  if (business.status === 'CLOSED') {
    return (
      <Card>
        <h2 className="font-semibold">Status</h2>
        <p className="mt-2 text-sm text-muted">Closed businesses cannot be reopened. Their data is deleted after 30 days.</p>
      </Card>
    )
  }

  return (
    <Card>
      <h2 className="mb-4 font-semibold">Status</h2>
      {change.error && <div className="mb-4"><Alert>{change.error.message}</Alert></div>}
      <div className="flex flex-col gap-3">
        {actions[business.status].map((a) => (
          <div key={a.target}>
            <Button className="w-full" busy={change.isPending && change.variables === a.target} onClick={() => change.mutate(a.target)}>
              {a.label}
            </Button>
            <p className="mt-1 text-xs text-muted">{a.explain}</p>
          </div>
        ))}
        {confirmClose ? (
          <div className="rounded-xl bg-danger-soft p-3">
            <p className="text-sm">
              Close <strong>{business.displayName}</strong>? Staff lose access immediately and this cannot be undone.
            </p>
            <div className="mt-3 flex gap-2">
              <Button variant="danger" busy={change.isPending && change.variables === 'CLOSED'} onClick={() => change.mutate('CLOSED')}>
                Close business
              </Button>
              <Button variant="ghost" onClick={() => setConfirmClose(false)}>
                Cancel
              </Button>
            </div>
          </div>
        ) : (
          <Button variant="danger" onClick={() => setConfirmClose(true)}>
            Close business…
          </Button>
        )}
      </div>
    </Card>
  )
}
