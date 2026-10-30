import { ChevronLeft, ChevronRight, DoorOpen, MessageSquareText, MoveRight, Plus, Users } from 'lucide-react'
import { useEffect, useRef, useState, type DragEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useDayCalendar, useReschedule, useWeekCalendar } from '../../api/bookingQueries'
import type { CalendarAppointment, CalendarColumn, DayCalendar } from '../../api/bookingTypes'
import { useMe, useStaffLocations } from '../../api/queries'
import { useToast } from '../../components/Toast'
import { Alert, Avatar, Button, EmptyState, PageHeader, SelectField, Skeleton } from '../../components/ui'
import { addDays, longDate, mondayOf, weekLabel } from '../schedule/schedule'
import { AppointmentDrawer } from './AppointmentDrawer'
import { NewBookingDrawer, type NewBookingStart } from './NewBookingDrawer'
import { AppointmentStatusBadge } from './status'
import { branchClock, hhmm, hourLabel, localMinutes, minutesOf, todayIn, zonedInstant } from './time'

/** Pixels per minute on the grid: an hour is 72px, enough for two lines in a 30-minute block. */
const SCALE = 1.2
/** Drops snap to this many minutes. */
const SNAP = 15

type View = 'day' | 'week'

/**
 * One time-grid column: a stylist on a day. The day view has a lane per stylist; the week view
 * for one stylist has a lane per day.
 */
interface Lane {
  key: string
  title: string
  subtitle?: string
  avatar?: string
  day: DayCalendar
  /** Null when the stylist has nothing that day (not rostered at this branch). */
  column: CalendarColumn | null
  staffId: string
}

interface PendingMove {
  appointment: CalendarAppointment
  lane: Lane
  minutes: number
}

const weekdayShort = (date: string) =>
  new Intl.DateTimeFormat('en-AU', { weekday: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(`${date}T00:00:00Z`))

/**
 * The calendar (DSH-4, DSH-5): the day at one branch with a column per stylist, or a week for one
 * stylist or the whole branch; an agenda list on narrow screens (DSH-6). Appointments can be dragged
 * to another time or stylist; the move is confirmed and the server refuses clashes (DSH-11).
 */
export function CalendarPage() {
  const me = useMe().data!
  const role = me.selected?.role
  const locations = useStaffLocations(true)
  const [params, setParams] = useSearchParams()
  const locationId = params.get('branch') ?? locations.data?.[0]?.id
  const branch = locations.data?.find((l) => l.id === locationId)
  const today = todayIn(branch?.timeZone ?? Intl.DateTimeFormat().resolvedOptions().timeZone)
  const date = params.get('date') ?? today
  const view: View = params.get('view') === 'week' ? 'week' : 'day'
  const staffFilter = params.get('staff') ?? 'all'
  const day = useDayCalendar(view === 'day' ? locationId : undefined, date)
  const week = useWeekCalendar(locationId, date, view === 'week')
  const [open, setOpen] = useState<string | null>(null)
  const [booking, setBooking] = useState<NewBookingStart | null>(null)
  const [pending, setPending] = useState<PendingMove | null>(null)
  const canBook = role !== 'STYLIST'

  const set = (change: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(change)) {
      if (v === null) next.delete(k)
      else next.set(k, v)
    }
    setParams(next, { replace: true })
  }
  const step = view === 'week' ? 7 : 1
  const loading = locations.isPending || (view === 'day' ? day.isPending : week.isPending)
  const error = view === 'day' ? day.error : week.error

  // Everyone who appears this week, for the stylist picker.
  const weekPeople = new Map<string, string>()
  week.data?.forEach((d) => d.columns.forEach((c) => weekPeople.set(c.staffId, c.displayName)))

  return (
    <>
      <PageHeader
        title="Calendar"
        description={branch ? `${branch.name}. Times are in ${branch.timeZone.replace('_', ' ')}.` : undefined}
        actions={
          canBook &&
          locationId && (
            <div className="flex gap-2">
              <Button variant="secondary" icon={DoorOpen} onClick={() => setBooking({ locationId, date: today, walkIn: true })}>
                Walk-in
              </Button>
              <Button icon={Plus} onClick={() => setBooking({ locationId, date })}>
                New booking
              </Button>
            </div>
          )
        }
      />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div role="radiogroup" aria-label="View" className="inline-flex rounded-lg bg-sunken p-1">
          {(['day', 'week'] as const).map((v) => (
            <button
              key={v}
              type="button"
              role="radio"
              aria-checked={view === v}
              onClick={() => set({ view: v === 'day' ? null : v })}
              className={`rounded-md px-3 py-1.5 text-[13px] font-medium transition-colors ${view === v ? 'bg-surface text-ink shadow-card' : 'text-muted hover:text-ink'}`}
            >
              {v === 'day' ? 'Day' : 'Week'}
            </button>
          ))}
        </div>
        <div className="flex items-center rounded-lg border border-line-strong bg-surface shadow-card">
          <button
            type="button"
            aria-label={view === 'week' ? 'Previous week' : 'Previous day'}
            onClick={() => set({ date: addDays(date, -step) })}
            className="rounded-l-lg p-2 text-muted hover:bg-sunken hover:text-ink"
          >
            <ChevronLeft className="size-4" aria-hidden />
          </button>
          <button
            type="button"
            aria-label={view === 'week' ? 'Next week' : 'Next day'}
            onClick={() => set({ date: addDays(date, step) })}
            className="rounded-r-lg border-l border-line p-2 text-muted hover:bg-sunken hover:text-ink"
          >
            <ChevronRight className="size-4" aria-hidden />
          </button>
        </div>
        <h2 className="pb-1.5 text-base font-semibold tabular-nums" aria-live="polite">
          {view === 'week' ? weekLabel(mondayOf(date)) : date === today ? `Today, ${longDate(date)}` : longDate(date)}
        </h2>
        {(view === 'week' ? mondayOf(date) !== mondayOf(today) : date !== today) && (
          <Button variant="ghost" size="sm" onClick={() => set({ date: null })}>
            {view === 'week' ? 'This week' : 'Today'}
          </Button>
        )}
        <div className="ml-auto flex flex-wrap gap-3">
          {view === 'week' && weekPeople.size > 1 && (
            <SelectField label="Stylist" value={staffFilter} onChange={(e) => set({ staff: e.target.value === 'all' ? null : e.target.value })} className="w-44">
              <option value="all">Everyone</option>
              {[...weekPeople].map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </SelectField>
          )}
          {(locations.data?.length ?? 0) > 1 && (
            <SelectField label="Branch" value={locationId ?? ''} onChange={(e) => set({ branch: e.target.value, staff: null })} className="w-48">
              {locations.data!.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </SelectField>
          )}
        </div>
      </div>

      {pending && <MoveConfirm key={pending.appointment.id} move={pending} onDone={() => setPending(null)} />}

      {loading && <Skeleton lines={5} />}
      {locations.data?.length === 0 && <EmptyState icon={Users} title="No branch yet">You are not assigned to a branch yet.</EmptyState>}
      {error && <Alert>{error.message}</Alert>}

      {view === 'day' && day.data && (
        <DayView
          day={day.data}
          today={today}
          role={role}
          busy={day.isPlaceholderData}
          canBook={canBook}
          onOpen={setOpen}
          onEmpty={(staffId) => setBooking({ locationId: day.data.locationId, date, staffId })}
          onDrop={setPending}
        />
      )}
      {view === 'week' && week.data && (
        <WeekView
          days={week.data}
          staffFilter={staffFilter}
          today={today}
          busy={week.isPlaceholderData}
          canBook={canBook}
          onOpen={setOpen}
          onPickDay={(d, staffId) => (canBook ? setBooking({ locationId: week.data[0]!.locationId, date: d, staffId }) : undefined)}
          onDrop={setPending}
        />
      )}

      {open && <AppointmentDrawer id={open} onClose={() => setOpen(null)} />}
      {booking && (
        <NewBookingDrawer
          key={JSON.stringify(booking)}
          start={booking}
          onClose={() => setBooking(null)}
          onBooked={(id) => {
            setBooking(null)
            setOpen(id)
          }}
        />
      )}
    </>
  )
}

/* ------------------------------------------------------------------ Views */

function DayView({
  day,
  today,
  role,
  busy,
  canBook,
  onOpen,
  onEmpty,
  onDrop,
}: {
  day: DayCalendar
  today: string
  role?: string
  busy: boolean
  canBook: boolean
  onOpen: (id: string) => void
  onEmpty: (staffId: string) => void
  onDrop: (move: PendingMove) => void
}) {
  if (day.columns.length === 0) {
    return (
      <EmptyState
        icon={Users}
        title="Nobody works at this branch yet"
        action={
          role === 'OWNER' && (
            <Link to="/staff" className="text-sm font-medium text-accent hover:underline">
              Go to Staff
            </Link>
          )
        }
      >
        Add staff to this branch and set their hours to see the day here.
      </EmptyState>
    )
  }
  const lanes: Lane[] = day.columns.map((c) => ({
    key: c.staffId,
    title: c.displayName,
    subtitle: c.bookableOnline ? undefined : 'Reception bookings only',
    avatar: c.displayName,
    day,
    column: c,
    staffId: c.staffId,
  }))
  return (
    <>
      {day.closed && (
        <div className="mb-4">
          <Alert tone="info">Closed{day.closureReason ? `: ${day.closureReason}` : ''}.</Alert>
        </div>
      )}
      <div className="hidden md:block">
        <TimeGrid
          lanes={lanes}
          today={today}
          busy={busy}
          onOpen={onOpen}
          onEmpty={canBook ? (lane) => onEmpty(lane.staffId) : undefined}
          onDrop={canBook ? onDrop : undefined}
        />
      </div>
      <div className="md:hidden">
        <DayList day={day} onOpen={onOpen} />
      </div>
    </>
  )
}

function WeekView({
  days,
  staffFilter,
  today,
  busy,
  canBook,
  onOpen,
  onPickDay,
  onDrop,
}: {
  days: DayCalendar[]
  staffFilter: string
  today: string
  busy: boolean
  canBook: boolean
  onOpen: (id: string) => void
  onPickDay: (date: string, staffId?: string) => void
  onDrop: (move: PendingMove) => void
}) {
  const person = staffFilter === 'all' ? null : staffFilter
  // A single stylist (or the only one): their week on a time grid, a lane per day.
  const only = new Set(days.flatMap((d) => d.columns.map((c) => c.staffId)))
  const staffId = person ?? (only.size === 1 ? [...only][0]! : null)
  if (staffId) {
    const lanes: Lane[] = days.map((d) => ({
      key: d.date,
      title: weekdayShort(d.date),
      subtitle: d.closed ? `Closed${d.closureReason ? `: ${d.closureReason}` : ''}` : d.opening.length === 0 ? 'Closed' : undefined,
      day: d,
      column: d.columns.find((c) => c.staffId === staffId) ?? null,
      staffId,
    }))
    return (
      <div className="overflow-hidden">
        <div className="hidden md:block">
          <TimeGrid
            lanes={lanes}
            today={today}
            busy={busy}
            onOpen={onOpen}
            onEmpty={canBook ? (lane) => onPickDay(lane.day.date, staffId) : undefined}
            onDrop={canBook ? onDrop : undefined}
            narrow
          />
        </div>
        <div className="md:hidden">
          <WeekAgenda days={days} staffId={staffId} today={today} onOpen={onOpen} />
        </div>
      </div>
    )
  }
  return <WeekAgenda days={days} staffId={null} today={today} onOpen={onOpen} onPickDay={canBook ? (d) => onPickDay(d) : undefined} />
}

/** The whole branch's week as seven short lists; select an appointment to open it. */
function WeekAgenda({
  days,
  staffId,
  today,
  onOpen,
  onPickDay,
}: {
  days: DayCalendar[]
  staffId: string | null
  today: string
  onOpen: (id: string) => void
  onPickDay?: (date: string) => void
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 2xl:grid-cols-7">
      {days.map((d) => {
        const items = d.columns
          .filter((c) => staffId === null || c.staffId === staffId)
          .flatMap((c) => c.appointments.filter((a) => !a.elsewhere && a.status !== 'CANCELLED').map((a) => ({ a, stylist: c.displayName })))
          .sort((x, y) => x.a.startAt.localeCompare(y.a.startAt))
        const closed = d.closed || d.opening.length === 0
        return (
          <section
            key={d.date}
            aria-label={longDate(d.date)}
            className={`flex min-w-0 flex-col rounded-xl border bg-surface shadow-card ${d.date === today ? 'border-accent/40' : 'border-line'}`}
          >
            <header className="border-b border-line px-3 py-2.5">
              <p className={`text-sm font-semibold ${d.date === today ? 'text-accent' : ''}`}>
                {weekdayShort(d.date)}
                {d.date === today && <span className="font-normal">, today</span>}
              </p>
              <p className="text-xs text-muted">{closed ? 'Closed' : `${items.length} booked`}</p>
            </header>
            <ul className="flex flex-1 flex-col gap-1.5 p-2">
              {items.map(({ a, stylist }) => (
                <li key={a.id}>
                  <button
                    type="button"
                    onClick={() => onOpen(a.id)}
                    className={`w-full rounded-md border-l-[3px] px-2 py-1.5 text-left text-xs ${blockStyle[a.status]}`}
                  >
                    <span className="block truncate">
                      <span className="font-semibold tabular-nums">{branchClock(a.startAt, d.timeZone)}</span> {a.customerName}
                    </span>
                    {staffId === null && <span className="block truncate text-muted">{stylist}</span>}
                  </button>
                </li>
              ))}
              {items.length === 0 && !closed && <li className="px-1 py-2 text-xs text-muted">Nothing booked</li>}
            </ul>
            {onPickDay && !closed && (
              <button type="button" onClick={() => onPickDay(d.date)} className="border-t border-line px-3 py-2 text-left text-xs font-medium text-accent hover:bg-sunken">
                Book this day
              </button>
            )}
          </section>
        )
      })}
    </div>
  )
}

/* ------------------------------------------------------------- Time grid */

function bounds(lanes: Lane[]) {
  const starts: number[] = []
  const ends: number[] = []
  for (const lane of lanes) {
    lane.day.opening.forEach((r) => {
      starts.push(minutesOf(r[0]))
      ends.push(minutesOf(r[1]))
    })
    lane.column?.work.forEach((w) => {
      starts.push(minutesOf(w[0]))
      ends.push(minutesOf(w[1]))
    })
    lane.column?.appointments.forEach((a) => {
      starts.push(localMinutes(a.startAt, lane.day.timeZone))
      ends.push(localMinutes(a.endAt, lane.day.timeZone) || 24 * 60)
    })
  }
  const from = starts.length ? Math.floor(Math.min(...starts) / 60) * 60 : 9 * 60
  const to = ends.length ? Math.ceil(Math.max(...ends) / 60) * 60 : 17 * 60
  return { from, to: Math.max(to, from + 60) }
}

function useNowMinutes(timeZone: string) {
  const [now, setNow] = useState(() => localMinutes(new Date(), timeZone))
  useEffect(() => {
    const timer = window.setInterval(() => setNow(localMinutes(new Date(), timeZone)), 60_000)
    return () => window.clearInterval(timer)
  }, [timeZone])
  return now
}

/** What is being dragged, and where on the block it was picked up (dataTransfer is unreadable during dragover). */
interface Drag {
  appointment: CalendarAppointment
  grabOffset: number
}

function TimeGrid({
  lanes,
  today,
  busy,
  onOpen,
  onEmpty,
  onDrop,
  narrow = false,
}: {
  lanes: Lane[]
  today: string
  busy: boolean
  onOpen: (id: string) => void
  onEmpty?: (lane: Lane) => void
  onDrop?: (move: PendingMove) => void
  narrow?: boolean
}) {
  const { from, to } = bounds(lanes)
  const height = (to - from) * SCALE
  const hours = Array.from({ length: (to - from) / 60 + 1 }, (_, i) => from + i * 60)
  const timeZone = lanes[0]!.day.timeZone
  const now = useNowMinutes(timeZone)
  const y = (minutes: number) => (minutes - from) * SCALE
  const [over, setOver] = useState<string | null>(null)
  const dragging = useRef<Drag | null>(null)

  function drop(event: DragEvent<HTMLDivElement>, lane: Lane) {
    event.preventDefault()
    setOver(null)
    const drag = dragging.current
    if (!drag || !onDrop) return
    const rect = event.currentTarget.getBoundingClientRect()
    const raw = from + (event.clientY - rect.top - drag.grabOffset) / SCALE
    const minutes = Math.max(from, Math.round(raw / SNAP) * SNAP)
    onDrop({ appointment: drag.appointment, lane, minutes })
    dragging.current = null
  }

  return (
    <div
      aria-busy={busy}
      className={`relative overflow-x-auto rounded-xl border border-line bg-surface shadow-card transition-opacity ${busy ? 'opacity-60' : ''}`}
    >
      <div className="flex min-w-fit">
        <div className="sticky left-0 z-10 w-16 shrink-0 border-r border-line bg-surface">
          <div className="h-14 border-b border-line" />
          <div className="relative" style={{ height }}>
            {hours.map((m) => (
              <span key={m} className="absolute right-2 -translate-y-1/2 text-xs text-muted tabular-nums" style={{ top: y(m) }}>
                {m < to ? hourLabel(m) : ''}
              </span>
            ))}
          </div>
        </div>
        {lanes.map((lane) => {
          const isToday = lane.day.date === today
          return (
            <div key={lane.key} className={`${narrow ? 'min-w-28' : 'min-w-44'} flex-1 border-r border-line last:border-r-0`}>
              <div className={`flex h-14 items-center gap-2.5 border-b border-line px-3 ${isToday && narrow ? 'bg-accent-soft/50' : ''}`}>
                {lane.avatar && <Avatar name={lane.avatar} size="sm" />}
                <div className="min-w-0">
                  <p className={`truncate text-sm font-medium ${isToday && narrow ? 'text-accent' : ''}`}>{lane.title}</p>
                  {lane.subtitle && <p className="truncate text-xs text-muted">{lane.subtitle}</p>}
                </div>
              </div>
              <div
                onDragOver={(e) => {
                  if (!dragging.current || !onDrop) return
                  e.preventDefault()
                  e.dataTransfer.dropEffect = 'move'
                  setOver(lane.key)
                }}
                onDragLeave={() => setOver((o) => (o === lane.key ? null : o))}
                onDrop={(e) => drop(e, lane)}
                className={over === lane.key ? 'outline-2 -outline-offset-2 outline-accent' : ''}
              >
                <LaneBody
                  lane={lane}
                  height={height}
                  y={y}
                  onOpen={onOpen}
                  onEmpty={onEmpty}
                  onDrag={onDrop ? (drag) => (dragging.current = drag) : undefined}
                />
              </div>
            </div>
          )
        })}
      </div>
      <div className="pointer-events-none absolute inset-x-0 top-14 left-16" style={{ height }} aria-hidden>
        {hours.slice(1, -1).map((m) => (
          <div key={m} className="absolute inset-x-0 border-t border-line/70" style={{ top: y(m) }} />
        ))}
        {!narrow && lanes[0]!.day.date === today && now >= from && now <= to && (
          <div className="absolute inset-x-0 border-t-2 border-danger" style={{ top: y(now) }}>
            <span className="absolute -top-[5px] -left-[5px] size-2 rounded-full bg-danger" />
          </div>
        )}
      </div>
    </div>
  )
}

function LaneBody({
  lane,
  height,
  y,
  onOpen,
  onEmpty,
  onDrag,
}: {
  lane: Lane
  height: number
  y: (minutes: number) => number
  onOpen: (id: string) => void
  onEmpty?: (lane: Lane) => void
  /** Present when appointments can be dragged; called with the drag on start and null on end. */
  onDrag?: (drag: Drag | null) => void
}) {
  const column = lane.column
  const leave = column?.timeOff.find((t) => t.kind === 'LEAVE')
  const visible = column?.appointments.filter((a) => a.status !== 'CANCELLED') ?? []
  const name = column?.displayName ?? lane.title
  return (
    // Not working: the tinted background shows through; working intervals are drawn on top in white.
    <div className="relative bg-line/75" style={{ height }}>
      {!leave &&
        column?.work.map(([start, end]) => (
          <button
            key={start}
            type="button"
            disabled={!onEmpty}
            aria-label={onEmpty ? `New booking with ${name}, ${lane.title}` : `${name} working ${hhmm(minutesOf(start))} to ${hhmm(minutesOf(end))}`}
            onClick={() => onEmpty?.(lane)}
            className="absolute inset-x-0 bg-surface enabled:hover:bg-accent-soft/40"
            style={{ top: y(minutesOf(start)), height: (minutesOf(end) - minutesOf(start)) * SCALE }}
          />
        ))}
      {leave && (
        <div className="absolute inset-0 flex items-start justify-center pt-6 text-sm text-muted">
          On leave{leave.reason ? `: ${leave.reason}` : ''}
        </div>
      )}
      {column?.timeOff
        .filter((t) => t.kind === 'BREAK' && t.from && t.to)
        .map((t) => (
          <div
            key={t.from}
            className="absolute inset-x-1 rounded-md border border-dashed border-line-strong bg-sunken px-2 py-1 text-xs text-muted"
            style={{ top: y(minutesOf(t.from!)), height: (minutesOf(t.to!) - minutesOf(t.from!)) * SCALE }}
          >
            Break{t.reason ? `: ${t.reason}` : ''}
          </div>
        ))}
      {visible.map((a) => (
        <AppointmentBlock key={a.id} a={a} day={lane.day} y={y} onOpen={onOpen} onDrag={onDrag} />
      ))}
    </div>
  )
}

const blockStyle: Record<CalendarAppointment['status'], string> = {
  CONFIRMED: 'border-accent bg-accent-soft text-ink',
  CHECKED_IN: 'border-success bg-success-soft text-ink',
  COMPLETED: 'border-line-strong bg-sunken text-muted',
  NO_SHOW: 'border-danger bg-danger-soft text-muted line-through',
  CANCELLED: 'hidden',
}

function AppointmentBlock({
  a,
  day,
  y,
  onOpen,
  onDrag,
}: {
  a: CalendarAppointment
  day: DayCalendar
  y: (m: number) => number
  onOpen: (id: string) => void
  onDrag?: (drag: Drag | null) => void
}) {
  const start = localMinutes(a.startAt, day.timeZone)
  const end = localMinutes(a.endAt, day.timeZone) || 24 * 60
  const blocked = localMinutes(a.blockedUntil, day.timeZone) || 24 * 60
  const top = y(start)
  const height = Math.max((end - start) * SCALE, 22)
  if (a.elsewhere) {
    return (
      <div
        className="absolute inset-x-1 rounded-md border border-dashed border-line-strong bg-surface/80 px-2 py-1 text-xs text-muted"
        style={{ top, height: (blocked - start) * SCALE }}
      >
        At {a.locationName}
      </div>
    )
  }
  // Only confirmed appointments can move; the panel's "Move to another time" does the same by keyboard.
  const movable = Boolean(onDrag) && a.status === 'CONFIRMED'
  return (
    <>
      {blocked > end && (
        <div
          aria-hidden
          title="Clean-up time"
          className="absolute inset-x-1 rounded-b-md bg-line/60"
          style={{ top: top + height, height: (blocked - end) * SCALE }}
        />
      )}
      <button
        type="button"
        draggable={movable}
        onDragStart={(e) => {
          onDrag?.({ appointment: a, grabOffset: e.clientY - e.currentTarget.getBoundingClientRect().top })
          e.dataTransfer.effectAllowed = 'move'
          e.dataTransfer.setData('text/plain', a.id)
        }}
        onDragEnd={() => onDrag?.(null)}
        onClick={() => onOpen(a.id)}
        aria-label={`${a.customerName}, ${branchClock(a.startAt, day.timeZone)}, ${a.services ?? ''}`}
        title={movable ? 'Drag to move' : undefined}
        className={`absolute inset-x-1 overflow-hidden rounded-md border-l-[3px] px-2 py-1 text-left text-xs shadow-card transition-transform duration-150 ease-out active:scale-[0.98] ${movable ? 'cursor-grab active:cursor-grabbing' : ''} ${blockStyle[a.status]}`}
        style={{ top, height }}
      >
        <span className="flex items-center gap-1 font-semibold">
          <span className="truncate">{a.customerName}</span>
          {a.hasNote && <MessageSquareText className="size-3 shrink-0" aria-label="Has a note" />}
        </span>
        {height > 30 && (
          <span className="block truncate tabular-nums">
            {branchClock(a.startAt, day.timeZone)} {a.services}
          </span>
        )}
      </button>
    </>
  )
}

/** Asks before a dropped appointment moves; the server refuses times that clash (DSH-11, DSH-12). */
function MoveConfirm({ move, onDone }: { move: PendingMove; onDone: () => void }) {
  const reschedule = useReschedule(move.appointment.id)
  const toast = useToast()
  const tz = move.lane.day.timeZone
  const start = zonedInstant(move.lane.day.date, move.minutes, tz)
  const stylist = move.lane.column?.displayName ?? move.lane.avatar ?? 'the same stylist'
  // Dropped back where it was: nothing to do.
  const sameLane = move.lane.column?.appointments.some((x) => x.id === move.appointment.id) ?? false
  const unchanged = sameLane && start === new Date(move.appointment.startAt).toISOString()
  return (
    <div role="alertdialog" aria-label="Move appointment" className="sticky top-2 z-20 mb-4 flex flex-wrap items-center gap-3 rounded-xl border border-accent/30 bg-surface px-4 py-3 shadow-overlay">
      <MoveRight className="size-4 text-accent" aria-hidden />
      <p className="min-w-0 flex-1 text-sm">
        Move <span className="font-medium">{move.appointment.customerName}</span> to{' '}
        <span className="font-medium tabular-nums">
          {longDate(move.lane.day.date)}, {branchClock(start, tz)}
        </span>{' '}
        with <span className="font-medium">{stylist}</span>?
      </p>
      {reschedule.error && <p className="w-full text-sm text-danger">{reschedule.error.message}</p>}
      <div className="flex gap-2">
        <Button variant="ghost" size="sm" onClick={onDone}>
          Cancel
        </Button>
        <Button
          size="sm"
          busy={reschedule.isPending}
          disabled={unchanged}
          onClick={() =>
            reschedule.mutate(
              { start, staffId: move.lane.staffId },
              {
                onSuccess: (moved) => {
                  toast(`Moved to ${branchClock(moved.startAt, moved.timeZone)} with ${moved.staffName}`)
                  onDone()
                },
              },
            )
          }
        >
          Move
        </Button>
      </div>
    </div>
  )
}

/* ------------------------------------------------------ Narrow screens (DSH-6) */

function DayList({ day, onOpen }: { day: DayCalendar; onOpen: (id: string) => void }) {
  return (
    <div className="flex flex-col gap-4">
      {day.columns.map((c) => {
        const items = c.appointments.filter((a) => !a.elsewhere && a.status !== 'CANCELLED')
        const leave = c.timeOff.find((t) => t.kind === 'LEAVE')
        return (
          <section key={c.staffId} aria-label={c.displayName} className="rounded-xl border border-line bg-surface shadow-card">
            <header className="flex items-center gap-2.5 border-b border-line px-4 py-3">
              <Avatar name={c.displayName} size="sm" />
              <p className="flex-1 font-medium">{c.displayName}</p>
              <p className="text-[13px] text-muted tabular-nums">
                {leave ? 'On leave' : c.work.length === 0 ? 'Not working' : c.work.map(([s, e]) => `${hhmm(minutesOf(s))}–${hhmm(minutesOf(e))}`).join(', ')}
              </p>
            </header>
            {items.length === 0 ? (
              <p className="px-4 py-3 text-sm text-muted">No appointments.</p>
            ) : (
              <ul className="divide-y divide-line">
                {items.map((a) => (
                  <li key={a.id}>
                    <button type="button" onClick={() => onOpen(a.id)} className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-sunken">
                      <span className="w-16 shrink-0 text-sm font-semibold tabular-nums">{branchClock(a.startAt, day.timeZone)}</span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium">{a.customerName}</span>
                        <span className="block truncate text-[13px] text-muted">{a.services}</span>
                      </span>
                      {a.status !== 'CONFIRMED' && <AppointmentStatusBadge status={a.status} />}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )
      })}
    </div>
  )
}
