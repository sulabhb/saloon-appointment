import { ChevronLeft, ChevronRight, Coffee, Pencil, Plane, Users } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useBranches, useRota } from '../../api/adminQueries'
import type { Rota, RotaDay, RotaRow } from '../../api/adminTypes'
import { useMe } from '../../api/queries'
import { Alert, Avatar, Badge, Button, EmptyState, PageHeader, SelectField, Skeleton } from '../../components/ui'
import { DayDrawer } from './DayDrawer'
import type { Branch } from './ShiftEditor'
import { StaffScheduleDrawer } from './StaffScheduleDrawer'
import { addDays, longDate, mondayOf, timeRange, today, weekLabel } from './schedule'

type Editing =
  | { kind: 'week'; row: RotaRow; tab?: 'week' | 'time-off' }
  | { kind: 'day'; row: RotaRow; day: RotaDay }

const weekdayShort = new Intl.DateTimeFormat('en-AU', { weekday: 'short', timeZone: 'UTC' })

/** Who works when and where, one week at a time (STF-4..6). */
export function SchedulePage() {
  const [params, setParams] = useSearchParams()
  const week = mondayOf(params.get('week') ?? today())
  const branchFilter = params.get('branch') ?? undefined
  const branches = useBranches()
  const rota = useRota(week, branchFilter)
  const role = useMe().data?.selected?.role
  const [editing, setEditing] = useState<Editing | null>(null)

  const branchList: Branch[] = (branches.data ?? []).map((b) => ({ id: b.id, name: b.name }))
  const go = (change: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(change)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setParams(next, { replace: true })
  }
  // People's branches the signed-in person may change; the API lists only those for managers.
  const workplacesOf = (row: RotaRow) => branchList.filter((b) => row.locationIds.includes(b.id))

  return (
    <>
      <PageHeader
        title="Schedule"
        description="Who works when, and where. Select a day to change just that date."
        actions={
          branchList.length > 1 && (
            <SelectField
              label="Branch"
              value={branchFilter ?? ''}
              onChange={(e) => go({ branch: e.target.value || undefined })}
              className="w-52"
            >
              <option value="">{role === 'OWNER' ? 'All branches' : 'All my branches'}</option>
              {branchList.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </SelectField>
          )
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="flex items-center rounded-lg border border-line-strong bg-surface shadow-card">
          <button
            type="button"
            aria-label="Previous week"
            onClick={() => go({ week: addDays(week, -7) })}
            className="rounded-l-lg p-2 text-muted hover:bg-sunken hover:text-ink"
          >
            <ChevronLeft className="size-4" aria-hidden />
          </button>
          <button
            type="button"
            aria-label="Next week"
            onClick={() => go({ week: addDays(week, 7) })}
            className="rounded-r-lg border-l border-line p-2 text-muted hover:bg-sunken hover:text-ink"
          >
            <ChevronRight className="size-4" aria-hidden />
          </button>
        </div>
        <h2 className="text-base font-semibold text-balance tabular-nums" aria-live="polite">
          {weekLabel(week)}
        </h2>
        {week !== mondayOf(today()) && (
          <Button variant="ghost" size="sm" onClick={() => go({ week: undefined })}>
            This week
          </Button>
        )}
      </div>

      {rota.isPending && <Skeleton lines={4} />}
      {rota.error && <Alert>{rota.error.message}</Alert>}
      {rota.data && rota.data.staff.length === 0 && (
        <EmptyState
          icon={Users}
          title={branchFilter ? 'Nobody works at this branch yet' : 'No staff yet'}
          action={
            <Link to="/staff" className="text-sm font-medium text-accent hover:underline">
              Go to Staff
            </Link>
          }
        >
          Add staff members and choose the branches they work at, then set their hours here.
        </EmptyState>
      )}
      {rota.data && rota.data.staff.length > 0 && (
        <RotaTable
          rota={rota.data}
          branches={branchList}
          branchFilter={branchFilter}
          busy={rota.isPlaceholderData}
          onEditWeek={(row) => setEditing({ kind: 'week', row })}
          onEditDay={(row, day) => setEditing({ kind: 'day', row, day })}
        />
      )}

      {editing?.kind === 'week' && (
        <StaffScheduleDrawer
          staffId={editing.row.staffId}
          name={editing.row.displayName}
          branches={workplacesOf(editing.row)}
          initialTab={editing.tab}
          onClose={() => setEditing(null)}
        />
      )}
      {editing?.kind === 'day' && (
        <DayDrawer
          key={editing.day.date}
          staffId={editing.row.staffId}
          name={editing.row.displayName}
          day={editing.day}
          workplaces={workplacesOf(editing.row)}
          onEditWeek={() => setEditing({ kind: 'week', row: editing.row })}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  )
}

function RotaTable({
  rota,
  branches,
  branchFilter,
  busy,
  onEditWeek,
  onEditDay,
}: {
  rota: Rota
  branches: Branch[]
  branchFilter?: string
  busy: boolean
  onEditWeek: (row: RotaRow) => void
  onEditDay: (row: RotaRow, day: RotaDay) => void
}) {
  const dates = rota.staff[0]!.days.map((d) => d.date)
  const now = today()
  const nameOf = (id: string) => branches.find((b) => b.id === id)?.name ?? 'Another branch'
  // Name the branch in a cell when it is not obvious from the filter.
  const showBranch = (locationId: string) => (branchFilter ? locationId !== branchFilter : branches.length > 1)
  const closedOn = (locationId: string, date: string) =>
    rota.closures.find((c) => c.locationId === locationId && c.startsOn <= date && c.endsOn >= date)

  return (
    <div
      aria-busy={busy}
      className={`relative overflow-x-auto rounded-xl border border-line bg-surface shadow-card transition-opacity ${busy ? 'opacity-60' : ''}`}
    >
      <table className="w-full min-w-[60rem] border-collapse text-sm">
        <caption className="sr-only">Staff schedule for the week of {weekLabel(rota.weekStart)}</caption>
        <thead>
          <tr className="border-b border-line">
            <th scope="col" className="sticky left-0 z-10 w-32 bg-surface px-3 py-3 sm:w-52 sm:px-4 text-left text-[13px] font-medium text-muted">
              Staff
            </th>
            {dates.map((date) => {
              const isToday = date === now
              const closure = branchFilter ? closedOn(branchFilter, date) : undefined
              return (
                <th key={date} scope="col" className="px-2 py-3 text-left font-normal">
                  <span className={`text-[13px] ${isToday ? 'font-semibold text-accent' : 'text-muted'}`}>
                    {weekdayShort.format(new Date(`${date}T00:00:00Z`))}{' '}
                    <span className="tabular-nums">{Number(date.slice(8))}</span>
                    {isToday && <span className="sr-only"> (today)</span>}
                  </span>
                  {closure && (
                    <span className="mt-1 block">
                      <Badge tone="warning">Closed</Badge>
                    </span>
                  )}
                </th>
              )
            })}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rota.staff.map((row) => {
            const noHours = row.days.every((d) => d.intervals.length === 0 && !d.override)
            return (
              <tr key={row.staffId}>
                <th scope="row" className="sticky left-0 z-10 bg-surface px-3 py-3 text-left align-top font-normal sm:px-4">
                  <div className="flex items-center gap-2.5">
                    <span className="hidden sm:inline-flex">
                      <Avatar name={row.displayName} size="sm" />
                    </span>
                    <div className="min-w-0">
                      <p className="truncate font-medium">{row.displayName}</p>
                      <button
                        type="button"
                        onClick={() => onEditWeek(row)}
                        className="inline-flex items-center gap-1 text-[13px] text-accent hover:underline"
                      >
                        <Pencil className="size-3" aria-hidden />
                        {noHours ? 'Set usual week' : 'Usual week'}
                        <span className="sr-only"> for {row.displayName}</span>
                      </button>
                    </div>
                  </div>
                </th>
                {row.days.map((day) => (
                  <td key={day.date} className="p-1.5 align-top">
                    <DayCell
                      row={row}
                      day={day}
                      isToday={day.date === now}
                      branchLabel={(id) => (showBranch(id) ? nameOf(id) : undefined)}
                      closedAt={(id) => Boolean(closedOn(id, day.date))}
                      onSelect={() => onEditDay(row, day)}
                    />
                  </td>
                ))}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function DayCell({
  row,
  day,
  isToday,
  branchLabel,
  closedAt,
  onSelect,
}: {
  row: RotaRow
  day: RotaDay
  isToday: boolean
  branchLabel: (locationId: string) => string | undefined
  closedAt: (locationId: string) => boolean
  onSelect: () => void
}) {
  const leave = day.timeOff.find((t) => t.kind === 'LEAVE')
  const breaks = day.timeOff.filter((t) => t.kind === 'BREAK')
  const work = day.intervals
    .map((iv) => {
      const where = branchLabel(iv.locationId)
      return `${timeRange(iv.startsAt, iv.endsAt)}${where ? ` at ${where}` : ''}${closedAt(iv.locationId) ? ' (branch closed)' : ''}`
    })
    .join(', ')
  const summary = [
    work || 'not working',
    leave && 'on leave',
    ...breaks.map((b) => `break ${timeRange(b.startsAt!, b.endsAt!)}`),
    day.override && 'changed for this date',
  ]
    .filter(Boolean)
    .join('; ')

  return (
    <button
      type="button"
      onClick={onSelect}
      aria-label={`${row.displayName}, ${longDate(day.date)}: ${summary}. Change this day.`}
      className={`relative flex min-h-16 w-full flex-col items-start gap-1 rounded-lg border p-2 text-left transition-colors hover:border-line-strong hover:bg-sunken/60 ${
        isToday ? 'border-accent/30' : 'border-transparent'
      } ${leave ? 'bg-sunken' : ''}`}
    >
      {day.override && <span aria-hidden className="absolute top-2 right-2 size-1.5 rounded-full bg-accent" />}
      {leave && (
        <span className="inline-flex items-center gap-1 text-[13px] font-medium text-ink">
          <Plane className="size-3.5 text-muted" aria-hidden />
          Leave
        </span>
      )}
      {day.intervals.map((iv) => {
        const where = branchLabel(iv.locationId)
        const closed = closedAt(iv.locationId)
        return (
          <span key={`${iv.locationId}-${iv.startsAt}`} className="flex flex-col leading-tight">
            <span className={`text-[13px] font-medium tabular-nums ${leave || closed ? 'text-muted line-through' : 'text-ink'}`}>
              {timeRange(iv.startsAt, iv.endsAt)}
            </span>
            {where && <span className="truncate text-xs text-muted">{where}</span>}
          </span>
        )
      })}
      {breaks.map((b) => (
        <span key={b.id} className="inline-flex items-center gap-1 text-xs text-muted tabular-nums">
          <Coffee className="size-3" aria-hidden />
          {timeRange(b.startsAt!, b.endsAt!)}
        </span>
      ))}
      {day.intervals.length === 0 && !leave && (
        <span className="text-[13px] text-subtle">{day.override ? 'Day off' : 'Off'}</span>
      )}
    </button>
  )
}
