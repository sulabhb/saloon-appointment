import { MailPlus, ShieldCheck, UserX } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import {
  useBranches,
  useDeactivateMember,
  useInviteTeamMember,
  useMembers,
  useRevokeTeamInvite,
  useTeamInvites,
} from '../../api/adminQueries'
import type { MemberView } from '../../api/adminTypes'
import { useMe } from '../../api/queries'
import type { InviteView, IssuedInvite, StaffRole } from '../../api/types'
import { Drawer } from '../../components/Drawer'
import { useToast } from '../../components/Toast'
import {
  Alert,
  Avatar,
  Badge,
  Button,
  ConfirmButton,
  Card,
  CheckboxList,
  CopyField,
  EmptyState,
  PageHeader,
  SelectField,
  Skeleton,
  StatusBadge,
  TextField,
  formatDate,
  roleLabel,
} from '../../components/ui'

/** Who can sign in to the business, with which role and branches (STF-2, STF-9). */
export function TeamPage() {
  const members = useMembers()
  const invites = useTeamInvites()
  const branches = useBranches()
  const [inviting, setInviting] = useState(false)
  const branchName = new Map((branches.data ?? []).map((b) => [b.id, b.name]))
  const openInvites = (invites.data ?? []).filter((i) => i.status === 'PENDING')

  return (
    <>
      <PageHeader
        title="Team & access"
        description="People who can sign in. Roles decide what they can see and change."
        actions={<Button icon={MailPlus} onClick={() => setInviting(true)}>Invite person</Button>}
      />
      <div className="flex flex-col gap-6">
        <Card title="Members" padded={false}>
          {members.isPending && <div className="p-5"><Skeleton lines={2} /></div>}
          {members.error && <div className="p-5"><Alert>{members.error.message}</Alert></div>}
          <ul className="divide-y divide-line">
            {members.data?.map((m) => (
              <MemberRow key={m.id} member={m} branchName={branchName} />
            ))}
          </ul>
        </Card>
        <Card title="Open invites" description="Links not used yet. They expire after 7 days." padded={false}>
          {openInvites.length === 0 ? (
            <div className="p-5">
              <EmptyState icon={MailPlus} title="No open invites" />
            </div>
          ) : (
            <InviteList invites={openInvites} />
          )}
        </Card>
      </div>
      <InviteDrawer open={inviting} onClose={() => setInviting(false)} />
    </>
  )
}

function MemberRow({ member, branchName }: { member: MemberView; branchName: Map<string, string> }) {
  const deactivate = useDeactivateMember()
  const toast = useToast()
  const me = useMe().data
  const [confirming, setConfirming] = useState(false)
  const isSelf = me?.user.id === member.userId
  const branches =
    member.role === 'OWNER'
      ? 'All branches'
      : member.locationIds.map((id) => branchName.get(id)).filter(Boolean).join(', ') || 'No branches'

  return (
    <li className="flex flex-wrap items-center gap-4 px-5 py-4">
      <Avatar name={member.fullName} />
      <div className="min-w-0 flex-1 basis-48">
        <p className={`truncate text-sm font-medium ${member.active ? '' : 'text-muted line-through'}`}>
          {member.fullName}
          {isSelf && <span className="ml-2 font-normal text-muted">(you)</span>}
        </p>
        <p className="truncate text-[13px] text-muted">{member.email}</p>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={member.role === 'OWNER' ? 'accent' : 'neutral'} icon={member.role === 'OWNER' ? ShieldCheck : undefined}>
          {roleLabel[member.role]}
        </Badge>
        <span className="text-[13px] text-muted">{branches}</span>
        {!member.active && <Badge>No access</Badge>}
      </div>
      {member.active && !isSelf && (
        <div className="ml-auto">
          {confirming ? (
            <div className="flex items-center gap-2">
              <span className="text-[13px] text-muted">Remove access?</span>
              <Button
                variant="danger"
                size="sm"
                busy={deactivate.isPending}
                onClick={() => deactivate.mutate(member.id, { onSuccess: () => toast(`${member.fullName} can no longer sign in`) })}
              >
                Remove
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setConfirming(false)}>Cancel</Button>
            </div>
          ) : (
            <Button variant="ghost" size="sm" icon={UserX} onClick={() => setConfirming(true)}>
              Remove access
            </Button>
          )}
        </div>
      )}
    </li>
  )
}

function InviteList({ invites }: { invites: InviteView[] }) {
  const revoke = useRevokeTeamInvite()
  const toast = useToast()
  return (
    <ul className="divide-y divide-line">
      {invites.map((i) => (
        <li key={i.id} className="flex flex-wrap items-center gap-4 px-5 py-3.5">
          <div className="min-w-0 flex-1 basis-48">
            <p className="truncate text-sm font-medium">{i.email}</p>
            <p className="text-[13px] text-muted">Sent {formatDate(i.createdAt)}</p>
          </div>
          <Badge>{roleLabel[i.role]}</Badge>
          <StatusBadge status={i.status} />
          <ConfirmButton
            label="Revoke"
            question="Stop this link working?"
            busy={revoke.isPending && revoke.variables === i.id}
            onConfirm={() => revoke.mutate(i.id, { onSuccess: () => toast('Invite revoked') })}
          />
        </li>
      ))}
    </ul>
  )
}

const ROLE_HELP: Record<StaffRole, string> = {
  OWNER: 'Everything: branches, services, staff and who can sign in.',
  MANAGER: 'Schedules, hours and services at their branches.',
  RECEPTION: 'All appointments at their branches.',
  STYLIST: 'Their own appointments.',
}

function InviteDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const invite = useInviteTeamMember()
  const branches = useBranches()
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<StaffRole>('RECEPTION')
  const [locationIds, setLocationIds] = useState<string[]>([])
  const [issued, setIssued] = useState<IssuedInvite | null>(null)

  function close() {
    setIssued(null)
    setEmail('')
    setLocationIds([])
    invite.reset()
    onClose()
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    invite.mutate({ email: email.trim(), role, locationIds }, { onSuccess: setIssued })
  }

  return (
    <Drawer
      open={open}
      onClose={close}
      title="Invite person"
      description="They get a link to create their sign-in. For stylists, invite them from their staff profile instead."
      footer={
        issued ? (
          <Button onClick={close}>Done</Button>
        ) : (
          <>
            <Button variant="ghost" onClick={close}>Cancel</Button>
            <Button type="submit" form="invite-form" busy={invite.isPending} disabled={!email.includes('@')}>
              Create invite link
            </Button>
          </>
        )
      }
    >
      {issued ? (
        <div className="flex flex-col gap-3">
          <Alert tone="success">Invite created for {issued.email}.</Alert>
          <CopyField label="Invite link" value={issued.url} />
          <p className="text-[13px] text-muted">Send it to them yourself. It works once, for 7 days, and is shown only now.</p>
        </div>
      ) : (
        <form id="invite-form" onSubmit={submit} className="flex flex-col gap-5">
          {invite.error && <Alert>{invite.error.message}</Alert>}
          <TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <SelectField label="Role" value={role} hint={ROLE_HELP[role]} onChange={(e) => setRole(e.target.value as StaffRole)}>
            {(['RECEPTION', 'MANAGER', 'STYLIST', 'OWNER'] as StaffRole[]).map((r) => (
              <option key={r} value={r}>{roleLabel[r]}</option>
            ))}
          </SelectField>
          {role !== 'OWNER' && (
            <CheckboxList
              legend="Branches they can access"
              items={(branches.data ?? []).filter((b) => b.active).map((b) => ({ id: b.id, label: b.name, detail: b.suburb }))}
              selected={locationIds}
              onChange={setLocationIds}
              empty="Add a branch first."
            />
          )}
        </form>
      )}
    </Drawer>
  )
}
