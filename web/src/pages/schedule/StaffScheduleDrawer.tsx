import { CalendarOff, Coffee, Copy, Plane, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { useAddTimeOff, useCheckTimeOff, useCheckWeekly, useRemoveTimeOff, useSaveWeekly, useStaffSchedule } from '../../api/adminQueries'
import type { StaffSchedule, TimeOffInput, TimeOffKind, WeeklyShift, WorkInterval } from '../../api/adminTypes'
import { Drawer } from '../../components/Drawer'
import { Tabs } from '../../components/Tabs'
import { useToast } from '../../components/Toast'
import { Alert, Button, ConfirmButton, EmptyState, Skeleton, TextField } from '../../components/ui'
import { IntervalRows, WarningsNotice, useCheckedSave, type Branch } from './ShiftEditor'
import { WEEKDAYS, intervalsError, longDate, timeRange } from './schedule'

type Tab = 'week' | 'time-off'

/** A person's usual week and their leave and breaks. */
export function StaffScheduleDrawer({
  staffId,
  name,
  branches,
  initialTab = 'week',
  onClose,
}: {
  staffId: string
  name: string
  branches: Branch[]
  initialTab?: Tab
  onClose: () => void
}) {
  const schedule = useStaffSchedule(staffId)
  const [tab, setTab] = useState<Tab>(initialTab)

  return (
    <Drawer open onClose={onClose} title={name} description="Times are local to each branch.">
      <Tabs
        label="Schedule"
        value={tab}
        onChange={setTab}
        tabs={[
          { id: 'week', label: 'Usual week' },
          { id: 'time-off', label: 'Leave & breaks' },
        ]}
      />
      {schedule.isPending && <Skeleton lines={5} />}
      {schedule.error && <Alert>{schedule.error.message}</Alert>}
      {schedule.data && tab === 'week' && (
        <WeekEditor key={JSON.stringify(schedule.data.weekly)} schedule={schedule.data} branches={branches} onSaved={onClose} />
      )}
      {schedule.data && tab === 'time-off' && <TimeOffEditor schedule={schedule.data} />}
    </Drawer>
  )
}

/* ---------------------------------------------------------------- Usual week */

function WeekEditor({ schedule, branches, onSaved }: { schedule: StaffSchedule; branches: Branch[]; onSaved: () => void }) {
  const toast = useToast()
  // Branches this person works at that the signed-in person may change; shifts elsewhere are shown but kept.
  const editable = branches.filter((b) => schedule.locationIds.includes(b.id))
  const isEditable = (s: WorkInterval) => editable.some((b) => b.id === s.locationId)
  const locked = schedule.weekly.filter((s) => !isEditable(s))
  const [week, setWeek] = useState<WorkInterval[][]>(() =>
    WEEKDAYS.map((_, i) => schedule.weekly.filter((s) => s.weekday === i + 1 && isEditable(s))),
  )
  const flow = useCheckedSave(useCheckWeekly(schedule.staffId), useSaveWeekly(schedule.staffId), () => {
    toast('Usual week saved')
    onSaved()
  })

  if (editable.length === 0) {
    return (
      <EmptyState
        icon={CalendarOff}
        title="No branch to schedule"
        action={
          <Link to="/staff" className="text-sm font-medium text-accent hover:underline">
            Go to Staff
          </Link>
        }
      >
        Choose where {schedule.displayName} works on their staff profile first.
      </EmptyState>
    )
  }

  const update = (day: number, intervals: WorkInterval[]) => {
    setWeek((w) => w.map((d, i) => (i === day ? intervals : d)))
    flow.reset()
  }
  const errors = week.map((intervals, day) => intervalsError([...intervals, ...locked.filter((s) => s.weekday === day + 1)]))
  const shifts: WeeklyShift[] = week.flatMap((intervals, day) => intervals.map((iv) => ({ ...iv, weekday: day + 1 })))
  const nameOf = (id: string) => branches.find((b) => b.id === id)?.name ?? 'another branch'

  return (
    <form
      onSubmit={(e: FormEvent) => {
        e.preventDefault()
        flow.submit(shifts)
      }}
      className="flex flex-col gap-5"
    >
      <div className="flex justify-end">
        <Button
          variant="secondary"
          size="sm"
          icon={Copy}
          disabled={week[0]!.length === 0}
          onClick={() => {
            setWeek((w) => w.map((d, i) => (i >= 1 && i <= 4 ? w[0]!.map((iv) => ({ ...iv })) : d)))
            flow.reset()
          }}
        >
          Copy Monday to weekdays
        </Button>
      </div>
      <ul className="-mx-1 divide-y divide-line">
        {WEEKDAYS.map((dayName, day) => {
          const intervals = week[day]!
          const working = intervals.length > 0
          const elsewhere = locked.filter((s) => s.weekday === day + 1)
          return (
            <li key={dayName} className="flex flex-col gap-2.5 px-1 py-3.5">
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  role="switch"
                  aria-checked={working}
                  aria-label={`Works on ${dayName}`}
                  onClick={() =>
                    update(day, working ? [] : [{ locationId: editable[0]!.id, startsAt: '09:00', endsAt: '17:00' }])
                  }
                  className={`relative inline-flex h-5 w-8 shrink-0 items-center rounded-full transition-colors ${working ? 'bg-accent' : 'bg-line-strong'}`}
                >
                  <span className={`inline-block size-4 rounded-full bg-white shadow transition-transform ${working ? 'translate-x-3.5' : 'translate-x-0.5'}`} />
                </button>
                <span className={`text-sm font-medium ${working ? '' : 'text-muted'}`}>{dayName}</span>
                {!working && elsewhere.length === 0 && <span className="text-sm text-subtle">Not working</span>}
              </div>
              {working && (
                <div className="pl-11">
                  <IntervalRows label={dayName} intervals={intervals} onChange={(iv) => update(day, iv)} branches={editable} />
                </div>
              )}
              {elsewhere.map((s) => (
                <p key={`${s.locationId}-${s.startsAt}`} className="pl-11 text-[13px] text-muted">
                  {timeRange(s.startsAt, s.endsAt)} at {nameOf(s.locationId)} (managed there)
                </p>
              ))}
              {errors[day] && <p className="pl-11 text-[13px] text-danger">{errors[day]}</p>}
            </li>
          )
        })}
      </ul>
      {flow.warnings && <WarningsNotice warnings={flow.warnings} />}
      {flow.error && <Alert>{flow.error.message}</Alert>}
      <div className="flex justify-end gap-2 border-t border-line pt-4">
        <Button type="submit" busy={flow.busy} disabled={errors.some(Boolean)}>
          {flow.warnings ? 'Save anyway' : 'Save usual week'}
        </Button>
      </div>
    </form>
  )
}

/* ------------------------------------------------------------ Leave & breaks */

function TimeOffEditor({ schedule }: { schedule: StaffSchedule }) {
  const add = useAddTimeOff(schedule.staffId)
  const remove = useRemoveTimeOff(schedule.staffId)
  const toast = useToast()
  const [kind, setKind] = useState<TimeOffKind>('LEAVE')
  const [startsOn, setStartsOn] = useState('')
  const [endsOn, setEndsOn] = useState('')
  const [startsAt, setStartsAt] = useState('12:00')
  const [endsAt, setEndsAt] = useState('13:00')
  const [reason, setReason] = useState('')

  const flow = useCheckedSave(useCheckTimeOff(schedule.staffId), add, () => {
    toast(kind === 'LEAVE' ? 'Leave added' : 'Break added')
    setStartsOn('')
    setEndsOn('')
    setReason('')
  })
  // Any edit makes earlier warnings stale.
  const edit =
    <T,>(set: (value: T) => void) =>
    (value: T) => {
      set(value)
      flow.reset()
    }

  const invalid =
    !startsOn || (kind === 'LEAVE' ? Boolean(endsOn) && endsOn < startsOn : startsAt >= endsAt)

  function submit(event: FormEvent) {
    event.preventDefault()
    const input: TimeOffInput =
      kind === 'LEAVE'
        ? { kind, startsOn, endsOn: endsOn || startsOn, reason: reason.trim() || undefined }
        : { kind, startsOn, startsAt, endsAt, reason: reason.trim() || undefined }
    flow.submit(input)
  }

  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby="time-off-upcoming">
        <h3 id="time-off-upcoming" className="mb-2 text-sm font-semibold">
          Coming up
        </h3>
        {schedule.timeOff.length === 0 ? (
          <p className="text-sm text-muted">No leave or breaks booked.</p>
        ) : (
          <ul className="divide-y divide-line rounded-xl border border-line">
            {schedule.timeOff.map((t) => (
              <li key={t.id} className="flex items-center gap-3 px-4 py-3">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-sunken text-muted">
                  {t.kind === 'LEAVE' ? <Plane className="size-4" aria-hidden /> : <Coffee className="size-4" aria-hidden />}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium tabular-nums">
                    {t.kind === 'LEAVE'
                      ? t.startsOn === t.endsOn
                        ? longDate(t.startsOn)
                        : `${longDate(t.startsOn)} – ${longDate(t.endsOn)}`
                      : `${longDate(t.startsOn)}, ${timeRange(t.startsAt!, t.endsAt!)}`}
                  </p>
                  <p className="truncate text-[13px] text-muted">
                    {t.kind === 'LEAVE' ? 'Leave' : 'Break'}
                    {t.reason && ` · ${t.reason}`}
                  </p>
                </div>
                <ConfirmButton
                  label={`Remove ${t.kind === 'LEAVE' ? 'leave' : 'break'} on ${longDate(t.startsOn)}`}
                  question="Remove?"
                  confirmLabel="Remove"
                  icon={Trash2}
                  iconOnly
                  busy={remove.isPending && remove.variables === t.id}
                  onConfirm={() => remove.mutate(t.id, { onSuccess: () => toast('Removed') })}
                />
              </li>
            ))}
          </ul>
        )}
        {remove.error && (
          <div className="mt-3">
            <Alert>{remove.error.message}</Alert>
          </div>
        )}
      </section>

      <form onSubmit={submit} className="flex flex-col gap-4 border-t border-line pt-5" aria-labelledby="time-off-add">
        <h3 id="time-off-add" className="text-sm font-semibold">
          Add
        </h3>
        <div role="radiogroup" aria-label="Kind" className="inline-flex w-fit rounded-lg bg-sunken p-1">
          {(['LEAVE', 'BREAK'] as const).map((k) => (
            <button
              key={k}
              type="button"
              role="radio"
              aria-checked={kind === k}
              onClick={() => edit(setKind)(k)}
              className={`rounded-md px-3 py-1.5 text-[13px] font-medium transition-colors ${
                kind === k ? 'bg-surface text-ink shadow-card' : 'text-muted hover:text-ink'
              }`}
            >
              {k === 'LEAVE' ? 'Leave (whole days)' : 'Break (part of a day)'}
            </button>
          ))}
        </div>
        {kind === 'LEAVE' ? (
          <div className="grid grid-cols-2 gap-3">
            <TextField label="First day" type="date" value={startsOn} onChange={(e) => edit(setStartsOn)(e.target.value)} />
            <TextField
              label="Last day"
              type="date"
              optional
              min={startsOn || undefined}
              value={endsOn}
              onChange={(e) => edit(setEndsOn)(e.target.value)}
              error={endsOn && startsOn && endsOn < startsOn ? 'Must be on or after the first day.' : undefined}
            />
          </div>
        ) : (
          <div className="grid grid-cols-[1.4fr_1fr_1fr] gap-3">
            <TextField label="Date" type="date" value={startsOn} onChange={(e) => edit(setStartsOn)(e.target.value)} />
            <TextField label="From" type="time" value={startsAt} onChange={(e) => edit(setStartsAt)(e.target.value)} />
            <TextField
              label="To"
              type="time"
              value={endsAt}
              onChange={(e) => edit(setEndsAt)(e.target.value)}
              error={startsAt >= endsAt ? 'After the start.' : undefined}
            />
          </div>
        )}
        <TextField
          label="Reason"
          optional
          maxLength={200}
          placeholder={kind === 'LEAVE' ? 'Annual leave' : 'Training'}
          hint="Only staff see this."
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        {flow.warnings && <WarningsNotice warnings={flow.warnings} />}
        {flow.error && <Alert>{flow.error.message}</Alert>}
        <div className="flex items-center gap-3">
          <Button type="submit" variant="secondary" busy={flow.busy} disabled={invalid}>
            {flow.warnings ? 'Add anyway' : kind === 'LEAVE' ? 'Add leave' : 'Add break'}
          </Button>
          {!startsOn && <p className="text-[13px] text-muted">Choose a date first.</p>}
        </div>
      </form>
    </div>
  )
}
