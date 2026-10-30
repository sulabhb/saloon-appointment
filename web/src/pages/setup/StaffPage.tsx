import { KeyRound, Mail, Plus, Scissors, UserPlus, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useBranches, useCatalog, useCreateStaff, useInviteTeamMember, useStaffProfiles, useUpdateStaff } from '../../api/adminQueries'
import type { StaffView } from '../../api/adminTypes'
import { useMe } from '../../api/queries'
import type { IssuedInvite, StaffRole } from '../../api/types'
import { Drawer } from '../../components/Drawer'
import { useToast } from '../../components/Toast'
import {
  Alert,
  Avatar,
  Badge,
  Button,
  CheckboxList,
  CopyField,
  EmptyState,
  PageHeader,
  SelectField,
  Skeleton,
  TextAreaField,
  TextField,
  Toggle,
  roleLabel,
} from '../../components/ui'

/** The people customers can book (STF-1..3, STF-8), and their sign-in. */
export function StaffPage() {
  const staff = useStaffProfiles()
  const branches = useBranches()
  const isOwner = useMe().data?.selected?.role === 'OWNER'
  const [editing, setEditing] = useState<StaffView | 'new' | null>(null)
  const branchName = new Map((branches.data ?? []).map((b) => [b.id, b.name]))

  return (
    <>
      <PageHeader
        title="Staff"
        description="Who customers can book, where they work and what they do."
        actions={isOwner && <Button icon={UserPlus} onClick={() => setEditing('new')}>Add staff member</Button>}
      />
      {staff.isPending && <Skeleton />}
      {staff.error && <Alert>{staff.error.message}</Alert>}
      {staff.data?.length === 0 && (
        <EmptyState
          icon={Users}
          title="No staff yet"
          action={isOwner && <Button icon={UserPlus} onClick={() => setEditing('new')}>Add staff member</Button>}
        >
          Add the stylists customers can book. Include yourself if you take appointments.
        </EmptyState>
      )}
      {staff.data && staff.data.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {staff.data.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                disabled={!isOwner}
                onClick={() => setEditing(s)}
                className={`flex h-full w-full flex-col rounded-xl border border-line bg-surface p-5 text-left shadow-card transition-colors enabled:hover:border-line-strong ${s.active ? '' : 'opacity-70'}`}
              >
                <div className="flex items-center gap-3">
                  <Avatar name={s.displayName} />
                  <div className="min-w-0">
                    <p className="truncate font-semibold">{s.displayName}</p>
                    <p className="truncate text-[13px] text-muted">
                      {s.locationIds.map((id) => branchName.get(id)).filter(Boolean).join(', ') || 'No branch yet'}
                    </p>
                  </div>
                </div>
                {s.bio && <p className="mt-3 line-clamp-2 text-sm text-muted">{s.bio}</p>}
                <div className="mt-auto flex flex-wrap gap-2 pt-4">
                  <Badge icon={Scissors} tone={s.serviceIds.length === 0 ? 'warning' : 'neutral'}>
                    {s.serviceIds.length} {s.serviceIds.length === 1 ? 'service' : 'services'}
                  </Badge>
                  {!s.active && <Badge>Inactive</Badge>}
                  {s.active && !s.bookableOnline && <Badge>Reception bookings only</Badge>}
                  {s.login?.status === 'ACTIVE' && <Badge tone="success" icon={KeyRound}>Can sign in</Badge>}
                  {s.login?.status === 'INVITED' && <Badge tone="warning" icon={Mail}>Invited</Badge>}
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
      {editing && <StaffDrawer key={editing === 'new' ? 'new' : editing.id} staff={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function StaffDrawer({ staff, onClose }: { staff?: StaffView; onClose: () => void }) {
  const create = useCreateStaff()
  const update = useUpdateStaff()
  const branches = useBranches()
  const catalog = useCatalog()
  const toast = useToast()
  const mutation = staff ? update : create

  const [displayName, setDisplayName] = useState(staff?.displayName ?? '')
  const [bio, setBio] = useState(staff?.bio ?? '')
  const [bookableOnline, setBookableOnline] = useState(staff?.bookableOnline ?? true)
  const [active, setActive] = useState(staff?.active ?? true)
  const [locationIds, setLocationIds] = useState(staff?.locationIds ?? [])
  const [serviceIds, setServiceIds] = useState(staff?.serviceIds ?? [])

  const services = (catalog.data ?? []).flatMap((c) =>
    c.services.filter((s) => s.active).map((s) => ({ id: s.id, label: s.name, detail: c.name })),
  )

  function submit(event: FormEvent) {
    event.preventDefault()
    const input = { displayName: displayName.trim(), bio: bio.trim() || undefined, bookableOnline, active, locationIds, serviceIds }
    const done = () => {
      toast(staff ? 'Staff member saved' : 'Staff member added')
      onClose()
    }
    if (staff) update.mutate({ ...input, id: staff.id }, { onSuccess: done })
    else create.mutate(input, { onSuccess: done })
  }

  return (
    <Drawer
      open
      onClose={onClose}
      title={staff ? staff.displayName : 'Add staff member'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="staff-form" busy={mutation.isPending} disabled={!displayName.trim()}>
            {staff ? 'Save' : 'Add staff member'}
          </Button>
        </>
      }
    >
      <form id="staff-form" onSubmit={submit} className="flex flex-col gap-5">
        {mutation.error && <Alert>{mutation.error.message}</Alert>}
        <TextField label="Name customers see" placeholder="Sam" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        <TextAreaField label="Short bio" optional maxLength={500} placeholder="Colour specialist with 10 years' experience." value={bio} onChange={(e) => setBio(e.target.value)} />
        <Toggle
          label="Bookable online"
          description="Off: only reception can book them, for example for trainees."
          checked={bookableOnline}
          onChange={setBookableOnline}
        />
        {staff && <Toggle label="Active" description="Inactive staff are hidden everywhere." checked={active} onChange={setActive} />}
        <CheckboxList
          legend="Works at"
          items={(branches.data ?? []).map((b) => ({ id: b.id, label: b.name, detail: b.suburb }))}
          selected={locationIds}
          onChange={setLocationIds}
          empty="Add a branch first."
        />
        <CheckboxList
          legend="Can perform"
          items={services}
          selected={serviceIds}
          onChange={setServiceIds}
          empty="Create services first."
        />
      </form>
      {staff && (
        <div className="mt-8 border-t border-line pt-6">
          <SignInSection staff={staff} />
        </div>
      )}
    </Drawer>
  )
}

/** Lets the person sign in to see their own appointments (STF-2). */
function SignInSection({ staff }: { staff: StaffView }) {
  const invite = useInviteTeamMember()
  const [email, setEmail] = useState(staff.login?.status === 'INVITED' ? staff.login.email : '')
  const [role, setRole] = useState<StaffRole>(staff.login?.role ?? 'STYLIST')
  const [issued, setIssued] = useState<IssuedInvite | null>(null)

  if (staff.login?.status === 'ACTIVE') {
    return (
      <div>
        <h3 className="text-sm font-semibold">Sign-in</h3>
        <p className="mt-1 text-sm text-muted">
          Signs in as <span className="text-ink">{staff.login.email}</span> · {roleLabel[staff.login.role]}
        </p>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h3 className="text-sm font-semibold">Invite to sign in</h3>
        <p className="mt-1 text-sm text-muted">
          {staff.login?.status === 'INVITED'
            ? `Invited as ${staff.login.email}. Sending again replaces the earlier link.`
            : 'Optional. Lets them see their own appointments on the web or in the app.'}
        </p>
      </div>
      <div className="grid grid-cols-[1fr_auto] gap-3">
        <TextField label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <SelectField label="Role" value={role} onChange={(e) => setRole(e.target.value as StaffRole)}>
          {(['STYLIST', 'MANAGER', 'OWNER'] as StaffRole[]).map((r) => (
            <option key={r} value={r}>{roleLabel[r]}</option>
          ))}
        </SelectField>
      </div>
      {invite.error && <Alert>{invite.error.message}</Alert>}
      <div>
        <Button
          variant="secondary"
          icon={Plus}
          busy={invite.isPending}
          disabled={!email.includes('@')}
          onClick={() =>
            invite.mutate(
              { email: email.trim(), role, staffId: staff.id, locationIds: staff.locationIds },
              { onSuccess: setIssued },
            )
          }
        >
          Create invite link
        </Button>
      </div>
      {issued && (
        <div className="flex flex-col gap-2">
          <CopyField label="Invite link" value={issued.url} />
          <p className="text-[13px] text-muted">Send this to {issued.email}. It works once and is shown only now.</p>
        </div>
      )}
    </div>
  )
}
