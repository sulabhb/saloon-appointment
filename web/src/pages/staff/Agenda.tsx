import { CalendarDays, ChevronLeft, ChevronRight, MessageSquareText, Phone } from 'lucide-react'
import { useState } from 'react'
import { AppointmentDrawer } from '../calendar/AppointmentDrawer'
import { AppointmentStatusBadge } from '../calendar/status'
import { useAgenda } from '../../api/queries'
import type { AgendaItem } from '../../api/types'
import { Alert, Card, EmptyState, Skeleton } from '../../components/ui'
import { addDays, longDate, today } from '../schedule/schedule'

/** A clock time in the branch's own time zone: "9:30 am". */
function branchTime(instant: string, timeZone: string) {
  return new Intl.DateTimeFormat('en-AU', { hour: 'numeric', minute: '2-digit', timeZone }).format(new Date(instant))
}

/** One day's appointments across the person's branches (DSH-1). */
export function Agenda() {
  const [date, setDate] = useState(today())
  const agenda = useAgenda(date, true)
  const items = agenda.data ?? []
  const branches = new Set(items.map((i) => i.locationId))
  const active = items.filter((i) => i.status !== 'CANCELLED')
  const label = date === today() ? 'Today' : longDate(date)
  const [open, setOpen] = useState<string | null>(null)

  return (
    <Card
      title={label}
      description={
        agenda.data ? `${active.length} ${active.length === 1 ? 'appointment' : 'appointments'}` : undefined
      }
      actions={
        <div className="flex items-center gap-1">
          <button
            type="button"
            aria-label="Previous day"
            onClick={() => setDate(addDays(date, -1))}
            className="rounded-lg p-1.5 text-muted hover:bg-sunken hover:text-ink"
          >
            <ChevronLeft className="size-4" aria-hidden />
          </button>
          {date !== today() && (
            <button
              type="button"
              onClick={() => setDate(today())}
              className="rounded-lg px-2 py-1 text-[13px] font-medium text-accent hover:bg-accent-soft"
            >
              Today
            </button>
          )}
          <button
            type="button"
            aria-label="Next day"
            onClick={() => setDate(addDays(date, 1))}
            className="rounded-lg p-1.5 text-muted hover:bg-sunken hover:text-ink"
          >
            <ChevronRight className="size-4" aria-hidden />
          </button>
        </div>
      }
      padded={false}
    >
      {agenda.isPending && (
        <div className="p-5">
          <Skeleton lines={3} />
        </div>
      )}
      {agenda.error && (
        <div className="p-5">
          <Alert>{agenda.error.message}</Alert>
        </div>
      )}
      {agenda.data?.length === 0 && (
        <div className="p-5">
          <EmptyState icon={CalendarDays} title="No appointments">
            Online bookings appear here as soon as customers make them.
          </EmptyState>
        </div>
      )}
      {items.length > 0 && (
        <ul className="divide-y divide-line" aria-live="polite">
          {items.map((item) => (
            <AgendaRow key={item.id} item={item} showBranch={branches.size > 1} onOpen={() => setOpen(item.id)} />
          ))}
        </ul>
      )}
      {open && <AppointmentDrawer id={open} onClose={() => setOpen(null)} />}
    </Card>
  )
}

function AgendaRow({ item, showBranch, onOpen }: { item: AgendaItem; showBranch: boolean; onOpen: () => void }) {
  const cancelled = item.status === 'CANCELLED'
  return (
    <li className={`flex gap-4 px-5 py-3.5 ${cancelled ? 'text-muted' : ''}`}>
      <div className="w-20 shrink-0 pt-0.5 text-sm tabular-nums">
        <p className="font-semibold">{branchTime(item.startAt, item.timeZone)}</p>
        <p className="text-[13px] text-muted">{branchTime(item.endAt, item.timeZone)}</p>
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" onClick={onOpen} className={`truncate text-left font-medium hover:underline ${cancelled ? 'line-through' : ''}`}>
            {item.customerName}
          </button>
          {item.status !== 'CONFIRMED' && <AppointmentStatusBadge status={item.status} />}
        </div>
        <p className="truncate text-sm text-muted">
          {item.services} with {item.staffName}
          {showBranch && ` · ${item.locationName}`}
        </p>
        {item.customerNote && (
          <p className="mt-1 flex items-start gap-1.5 text-[13px] text-muted">
            <MessageSquareText className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            <span className="line-clamp-2">{item.customerNote}</span>
          </p>
        )}
      </div>
      {item.customerPhone && !cancelled && (
        <a
          href={`tel:${item.customerPhone}`}
          aria-label={`Call ${item.customerName}`}
          className="self-start rounded-lg p-2 text-muted hover:bg-sunken hover:text-ink"
        >
          <Phone className="size-4" aria-hidden />
        </a>
      )}
    </li>
  )
}
