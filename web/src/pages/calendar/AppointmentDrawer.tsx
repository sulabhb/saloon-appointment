import { AlertTriangle, CalendarClock, Check, Clock3, Mail, MessageSquareText, Phone, X } from 'lucide-react'
import { useState } from 'react'
import { useAppointment, useChangeStatus, useReschedule, useSaveInternalNote } from '../../api/bookingQueries'
import type { AppointmentDetail, EmailRecord } from '../../api/bookingTypes'
import type { AppointmentStatus } from '../../api/types'
import { Drawer } from '../../components/Drawer'
import { useToast } from '../../components/Toast'
import { Alert, Badge, Button, Skeleton, TextAreaField, TextField, formatDuration, formatMoney } from '../../components/ui'
import { AppointmentStatusBadge, actionLabel, statusMeta } from './status'
import { SlotPicker } from './SlotPicker'
import { branchClock, branchDate } from './time'

/** "Online", "By Jo Rivera", "Walk-in, added by Jo Rivera". */
function bookedBy(a: AppointmentDetail) {
  switch (a.source) {
    case 'web':
      return 'Online'
    case 'android':
      return 'In the app'
    case 'staff':
      return a.createdByName ? `By ${a.createdByName}` : 'By staff'
    case 'walk_in':
      return a.createdByName ? `Walk-in, added by ${a.createdByName}` : 'Walk-in'
  }
}

/** One appointment for staff (DSH-8): details, status, internal note and moving it. */
export function AppointmentDrawer({ id, onClose }: { id: string; onClose: () => void }) {
  const appointment = useAppointment(id)
  const a = appointment.data
  return (
    <Drawer
      open
      onClose={onClose}
      title={a ? a.customer.fullName : 'Appointment'}
      description={a ? `${branchDate(a.startAt, a.timeZone)}, ${branchClock(a.startAt, a.timeZone)}` : undefined}
    >
      {appointment.isPending && <Skeleton lines={4} />}
      {appointment.error && <Alert>{appointment.error.message}</Alert>}
      {a && <AppointmentBody key={a.id} a={a} />}
    </Drawer>
  )
}

function AppointmentBody({ a }: { a: AppointmentDetail }) {
  const [moving, setMoving] = useState(false)
  return (
    <div className="flex flex-col gap-6">
      <StatusSection a={a} />
      {moving ? (
        <MoveSection a={a} onDone={() => setMoving(false)} />
      ) : (
        a.canReschedule && (
          <div>
            <Button variant="secondary" size="sm" icon={CalendarClock} onClick={() => setMoving(true)}>
              Move to another time
            </Button>
          </div>
        )
      )}
      <Details a={a} />
      <NoteSection a={a} />
      <EmailsSection a={a} />
    </div>
  )
}

function StatusSection({ a }: { a: AppointmentDetail }) {
  const change = useChangeStatus(a.id)
  const toast = useToast()
  const [cancelling, setCancelling] = useState(false)
  const [reason, setReason] = useState('')
  const go = (status: AppointmentStatus, why?: string) =>
    change.mutate(
      { status, reason: why },
      {
        onSuccess: () => {
          setCancelling(false)
          toast(statusMeta[status].label)
        },
      },
    )
  const actions = a.allowedTransitions.filter((s) => s !== 'CANCELLED')
  const canCancel = a.allowedTransitions.includes('CANCELLED')

  return (
    <section aria-label="Status" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <AppointmentStatusBadge status={a.status} />
        {a.status === 'CANCELLED' && (
          <span className="text-[13px] text-muted">
            by {a.cancelledBy === 'customer' ? 'the customer' : 'staff'}
            {a.lateCancel && ', after the cut-off'}
            {a.cancelReason && `: ${a.cancelReason}`}
          </span>
        )}
      </div>
      {(actions.length > 0 || canCancel) && !cancelling && (
        <div className="flex flex-wrap gap-2">
          {actions.map((s) => (
            <Button
              key={s}
              size="sm"
              variant={s === 'NO_SHOW' ? 'secondary' : 'primary'}
              icon={statusMeta[s].icon}
              busy={change.isPending && change.variables?.status === s}
              onClick={() => go(s)}
            >
              {actionLabel(a.status, s)}
            </Button>
          ))}
          {canCancel && (
            <Button size="sm" variant="ghost" icon={X} onClick={() => setCancelling(true)}>
              Cancel appointment
            </Button>
          )}
        </div>
      )}
      {cancelling && (
        <div className="flex flex-col gap-3 rounded-lg border border-line p-4">
          <TextField label="Reason" optional placeholder="Called to cancel" value={reason} onChange={(e) => setReason(e.target.value)} />
          <div className="flex gap-2">
            <Button size="sm" variant="ghost" onClick={() => setCancelling(false)}>
              Keep it
            </Button>
            <Button
              size="sm"
              variant="danger"
              busy={change.isPending}
              onClick={() => go('CANCELLED', reason.trim() || undefined)}
            >
              Cancel appointment
            </Button>
          </div>
        </div>
      )}
      {change.error && <Alert>{change.error.message}</Alert>}
    </section>
  )
}

function MoveSection({ a, onDone }: { a: AppointmentDetail; onDone: () => void }) {
  const reschedule = useReschedule(a.id)
  const toast = useToast()
  const [date, setDate] = useState(() => new Intl.DateTimeFormat('en-CA', { timeZone: a.timeZone }).format(new Date(a.startAt)))
  const [staffId, setStaffId] = useState<string | null>(a.staffId)
  const [start, setStart] = useState<string | null>(null)
  return (
    <section aria-labelledby="move" className="flex flex-col gap-4 rounded-lg border border-line p-4">
      <div className="flex items-center justify-between">
        <h3 id="move" className="text-sm font-semibold">
          Move to another time
        </h3>
        <Button variant="ghost" size="sm" onClick={onDone}>
          Close
        </Button>
      </div>
      <SlotPicker
        locationId={a.locationId}
        serviceIds={a.services.map((s) => s.serviceId)}
        date={date}
        onDate={(d) => {
          setDate(d)
          setStart(null)
        }}
        staffId={staffId}
        onStaff={(id) => {
          setStaffId(id)
          setStart(null)
        }}
        start={start}
        onStart={setStart}
        reschedule={a.id}
      />
      {reschedule.error && <Alert>{reschedule.error.message}</Alert>}
      <div>
        <Button
          disabled={!start}
          busy={reschedule.isPending}
          onClick={() =>
            start &&
            reschedule.mutate(
              { start, staffId },
              {
                onSuccess: (moved) => {
                  toast(`Moved to ${branchClock(moved.startAt, moved.timeZone)} with ${moved.staffName}`)
                  onDone()
                },
              },
            )
          }
        >
          Move here
        </Button>
      </div>
    </section>
  )
}

function Details({ a }: { a: AppointmentDetail }) {
  const rows: [string, React.ReactNode][] = [
    [
      'When',
      <span key="when" className="tabular-nums">
        {branchClock(a.startAt, a.timeZone)} to {branchClock(a.endAt, a.timeZone)}
      </span>,
    ],
    [
      'Services',
      <div key="services">
        {a.services.map((s) => (
          <p key={s.serviceId} className="flex justify-between gap-4">
            <span>{s.name}</span>
            <span className="text-muted tabular-nums">
              {formatDuration(s.durationMin)}, {s.priceIsFrom && 'from '}
              {formatMoney(s.priceCents)}
            </span>
          </p>
        ))}
      </div>,
    ],
    ['Stylist', a.staffName],
    ['Branch', a.locationName],
    [
      'Customer',
      <div key="customer" className="flex flex-col gap-1">
        {a.customer.phone && (
          <a href={`tel:${a.customer.phone}`} className="inline-flex items-center gap-1.5 tabular-nums hover:underline">
            <Phone className="size-3.5 text-muted" aria-hidden />
            {a.customer.phone}
          </a>
        )}
        {a.customer.email && (
          <a href={`mailto:${a.customer.email}`} className="inline-flex items-center gap-1.5 hover:underline">
            <Mail className="size-3.5 text-muted" aria-hidden />
            {a.customer.email}
          </a>
        )}
        {!a.customer.phone && !a.customer.email && <span className="text-muted">No contact details</span>}
      </div>,
    ],
    ['Reference', <span key="ref" className="font-medium tabular-nums">{a.reference}</span>],
    [
      'Booked',
      `${bookedBy(a)}, ${new Intl.DateTimeFormat('en-AU', {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: a.timeZone,
      }).format(new Date(a.createdAt))}`,
    ],
  ]
  return (
    <section aria-label="Details">
      <dl className="divide-y divide-line border-y border-line text-sm">
        {rows.map(([label, value]) => (
          <div key={label} className="flex gap-4 py-2.5">
            <dt className="w-24 shrink-0 text-muted">{label}</dt>
            <dd className="min-w-0 flex-1">{value}</dd>
          </div>
        ))}
      </dl>
      {a.customerNote && (
        <p className="mt-4 flex items-start gap-2 rounded-lg bg-sunken px-3 py-2.5 text-sm">
          <MessageSquareText className="mt-0.5 size-4 shrink-0 text-muted" aria-hidden />
          <span>
            <span className="font-medium">From the customer: </span>
            {a.customerNote}
          </span>
        </p>
      )}
    </section>
  )
}

function NoteSection({ a }: { a: AppointmentDetail }) {
  const save = useSaveInternalNote(a.id)
  const toast = useToast()
  const [note, setNote] = useState(a.internalNote ?? '')
  if (!a.canEditNote) {
    return a.internalNote ? (
      <section aria-label="Staff note">
        <h3 className="mb-1 text-sm font-semibold">Staff note</h3>
        <p className="text-sm whitespace-pre-line text-muted">{a.internalNote}</p>
      </section>
    ) : null
  }
  const dirty = note.trim() !== (a.internalNote ?? '')
  return (
    <section aria-label="Staff note" className="flex flex-col gap-2">
      <TextAreaField
        label="Staff note"
        optional
        hint="Only staff see this."
        maxLength={2000}
        value={note}
        onChange={(e) => setNote(e.target.value)}
      />
      {save.error && <Alert>{save.error.message}</Alert>}
      {dirty && (
        <div>
          <Button size="sm" variant="secondary" busy={save.isPending} onClick={() => save.mutate(note, { onSuccess: () => toast('Note saved') })}>
            Save note
          </Button>
        </div>
      )}
    </section>
  )
}

const emailLabel: Record<EmailRecord['type'], string> = {
  booking_confirmed: 'Confirmation',
  booking_rescheduled: 'New time',
  booking_cancelled: 'Cancellation',
  reminder: 'Reminder',
  manage_link: 'New booking link',
}

/** What the customer was emailed, and anything that did not go out (NTF-5). */
function EmailsSection({ a }: { a: AppointmentDetail }) {
  if (a.emails.length === 0) {
    return a.customer.email ? null : (
      <p className="text-[13px] text-muted">No email address, so the customer gets no emails about this booking.</p>
    )
  }
  return (
    <section aria-labelledby="emails">
      <h3 id="emails" className="mb-2 text-sm font-semibold">
        Emails to the customer
      </h3>
      <ul className="divide-y divide-line rounded-lg border border-line text-sm">
        {a.emails.map((e) => (
          <li key={`${e.type}-${e.createdAt}`} className="flex flex-col gap-1 px-3 py-2.5">
            <div className="flex items-center justify-between gap-3">
              <span className="font-medium">{emailLabel[e.type]}</span>
              {e.status === 'sent' && (
                <Badge tone="success" icon={Check}>
                  Sent
                </Badge>
              )}
              {(e.status === 'queued' || e.status === 'sending') && (
                <Badge icon={Clock3}>{e.attempts > 0 ? `Retrying (try ${e.attempts + 1})` : 'Sending soon'}</Badge>
              )}
              {e.status === 'failed' && (
                <Badge tone="danger" icon={AlertTriangle}>
                  Not sent
                </Badge>
              )}
              {e.status === 'skipped' && <Badge>Not needed</Badge>}
            </div>
            <span className="text-[13px] text-muted">
              {e.recipient}
              {e.sentAt &&
                `, ${new Intl.DateTimeFormat('en-AU', { dateStyle: 'medium', timeStyle: 'short', timeZone: a.timeZone }).format(new Date(e.sentAt))}`}
            </span>
            {e.status === 'failed' && (
              <span className="text-[13px] text-danger">
                Gave up after {e.attempts} tries. Contact the customer another way.
                {e.lastError && <span className="block text-muted">{e.lastError}</span>}
              </span>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
