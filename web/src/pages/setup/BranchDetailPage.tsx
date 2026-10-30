import { ArrowLeft, CalendarOff, Copy, MapPin, Plus, Scissors, Trash2, X } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import {
  useAddClosure,
  useBranch,
  useOverview,
  useRemoveClosure,
  useSaveHours,
  useSetOffering,
  useUpdateBranch,
} from '../../api/adminQueries'
import type { BranchDetail, BranchService, HoursInterval } from '../../api/adminTypes'
import { ApiError } from '../../api/client'
import { useMe } from '../../api/queries'
import { Tabs } from '../../components/Tabs'
import { useToast } from '../../components/Toast'
import {
  Alert,
  Badge,
  Button,
  Card,
  ConfirmButton,
  EmptyState,
  PageHeader,
  Skeleton,
  TextField,
  Toggle,
  formatDay,
  formatDuration,
  formatMoney,
} from '../../components/ui'
import { BranchRulesCard } from './BookingRules'
import { BranchForm } from './BranchForm'

type Tab = 'hours' | 'services' | 'closures' | 'details'

export function BranchDetailPage() {
  const { branchId = '' } = useParams()
  const branch = useBranch(branchId)
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') as Tab | null) ?? 'hours'

  if (branch.isPending) return <Skeleton lines={4} />
  if (branch.error) return <Alert>{branch.error.message}</Alert>

  const b = branch.data
  return (
    <>
      <Link to="/branches" className="mb-3 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
        <ArrowLeft className="size-4" aria-hidden />
        Branches
      </Link>
      <PageHeader
        title={b.name}
        description={
          <span className="inline-flex flex-wrap items-center gap-2 text-sm">
            <MapPin className="size-3.5" aria-hidden />
            {b.addressLine1}, {b.suburb} {b.state} {b.postcode}
            {!b.active && <Badge>Inactive</Badge>}
          </span>
        }
      />
      <Tabs
        label="Branch settings"
        value={tab}
        onChange={(t) => setParams({ tab: t }, { replace: true })}
        tabs={[
          { id: 'hours', label: 'Opening hours' },
          { id: 'services', label: 'Services' },
          { id: 'closures', label: 'Closures' },
          { id: 'details', label: 'Details' },
        ]}
      />
      {tab === 'hours' && <HoursEditor key={JSON.stringify(b.hours)} branch={b} />}
      {tab === 'services' && <ServicesTab branch={b} />}
      {tab === 'closures' && <ClosuresTab branch={b} />}
      {tab === 'details' && <DetailsTab branch={b} />}
    </>
  )
}

/* -------------------------------------------------------------- Opening hours */

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

type Interval = { opensAt: string; closesAt: string }

function toWeek(hours: HoursInterval[]): Interval[][] {
  return DAYS.map((_, i) =>
    hours
      .filter((h) => h.weekday === i + 1)
      .map((h) => ({ opensAt: h.opensAt.slice(0, 5), closesAt: h.closesAt.slice(0, 5) })),
  )
}

/**
 * Adds a second time range. A day that runs through lunch is split into a midday break
 * (09:00–17:00 becomes 09:00–12:30 and 13:30–17:00); otherwise an evening range follows.
 */
export function addTimeRange(intervals: Interval[]): Interval[] {
  const last = intervals.at(-1)
  if (!last) return [{ opensAt: '09:00', closesAt: '17:00' }]
  if (last.opensAt < '12:30' && last.closesAt > '13:30') {
    return [...intervals.slice(0, -1), { opensAt: last.opensAt, closesAt: '12:30' }, { opensAt: '13:30', closesAt: last.closesAt }]
  }
  const [h, m] = last.closesAt.split(':').map(Number) as [number, number]
  const startHour = Math.min(h + 1, 22)
  const pad = (n: number) => String(n).padStart(2, '0')
  return [...intervals, { opensAt: `${pad(startHour)}:${pad(m)}`, closesAt: `${pad(Math.min(startHour + 2, 23))}:${pad(m)}` }]
}

/** What is wrong with a day's ranges, if anything. Same rules as the server. */
export function dayError(intervals: Interval[]): string | undefined {
  if (intervals.some((iv) => iv.opensAt >= iv.closesAt)) return 'Opening time must be before closing time.'
  const sorted = [...intervals].sort((a, b) => a.opensAt.localeCompare(b.opensAt))
  if (sorted.some((iv, i) => i > 0 && iv.opensAt < sorted[i - 1]!.closesAt)) return 'Time ranges overlap.'
  return undefined
}

function HoursEditor({ branch }: { branch: BranchDetail }) {
  const save = useSaveHours(branch.id)
  const toast = useToast()
  const [week, setWeek] = useState<Interval[][]>(() => toWeek(branch.hours))
  const [dirty, setDirty] = useState(false)

  const update = (day: number, intervals: Interval[]) => {
    setWeek((w) => w.map((d, i) => (i === day ? intervals : d)))
    setDirty(true)
  }
  const errors = week.map(dayError)
  const hasErrors = errors.some(Boolean)

  function submit() {
    const hours = week.flatMap((intervals, day) => intervals.map((iv) => ({ weekday: day + 1, ...iv })))
    save.mutate(hours, {
      onSuccess: () => {
        setDirty(false)
        toast('Opening hours saved')
      },
    })
  }

  return (
    <Card
      title="Weekly opening hours"
      description={`Times are in ${branch.timeZone.replace('_', ' ')}. Add a second time range for a midday break.`}
      actions={
        <Button
          variant="secondary"
          size="sm"
          icon={Copy}
          disabled={week[0]!.length === 0}
          onClick={() => {
            setWeek((w) => w.map((d, i) => (i >= 1 && i <= 4 ? w[0]!.map((iv) => ({ ...iv })) : d)))
            setDirty(true)
          }}
        >
          Copy Monday to weekdays
        </Button>
      }
      padded={false}
    >
      <ul className="divide-y divide-line">
        {week.map((intervals, day) => {
          const open = intervals.length > 0
          return (
            <li key={DAYS[day]} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-start">
              <div className="flex w-40 shrink-0 items-center gap-3 sm:pt-1.5">
                <button
                  type="button"
                  role="switch"
                  aria-checked={open}
                  aria-label={`${DAYS[day]} open`}
                  onClick={() => update(day, open ? [] : [{ opensAt: '09:00', closesAt: '17:00' }])}
                  className={`relative inline-flex h-5 w-8 shrink-0 items-center rounded-full transition-colors ${open ? 'bg-accent' : 'bg-line-strong'}`}
                >
                  <span className={`inline-block size-4 rounded-full bg-white shadow transition-transform ${open ? 'translate-x-3.5' : 'translate-x-0.5'}`} />
                </button>
                <span className={`text-sm font-medium ${open ? '' : 'text-muted'}`}>{DAYS[day]}</span>
              </div>
              {open ? (
                <div className="flex flex-1 flex-col gap-2">
                  {intervals.map((iv, i) => (
                    <div key={i} className="flex flex-wrap items-center gap-2">
                      <input
                        type="time"
                        aria-label={`${DAYS[day]} opens`}
                        value={iv.opensAt}
                        onChange={(e) => update(day, intervals.map((x, j) => (j === i ? { ...x, opensAt: e.target.value } : x)))}
                        className="h-9 rounded-lg border border-line-strong bg-surface px-2.5 text-sm tabular-nums"
                      />
                      <span className="text-muted">–</span>
                      <input
                        type="time"
                        aria-label={`${DAYS[day]} closes`}
                        value={iv.closesAt}
                        onChange={(e) => update(day, intervals.map((x, j) => (j === i ? { ...x, closesAt: e.target.value } : x)))}
                        className="h-9 rounded-lg border border-line-strong bg-surface px-2.5 text-sm tabular-nums"
                      />
                      {intervals.length > 1 && (
                        <button
                          type="button"
                          aria-label="Remove time range"
                          onClick={() => update(day, intervals.filter((_, j) => j !== i))}
                          className="rounded-lg p-1.5 text-muted hover:bg-sunken hover:text-ink"
                        >
                          <X className="size-4" aria-hidden />
                        </button>
                      )}
                      {i === intervals.length - 1 && (
                        <button
                          type="button"
                          onClick={() => update(day, addTimeRange(intervals))}
                          className="inline-flex items-center gap-1 rounded-lg px-2 py-1.5 text-[13px] font-medium text-accent hover:bg-accent-soft"
                        >
                          <Plus className="size-3.5" aria-hidden />
                          {iv.opensAt < '12:30' && iv.closesAt > '13:30' ? 'Add lunch break' : 'Add time range'}
                        </button>
                      )}
                    </div>
                  ))}
                  {errors[day] && <p className="text-[13px] text-danger">{errors[day]}</p>}
                </div>
              ) : (
                <p className="text-sm text-muted sm:pt-1.5">Closed</p>
              )}
            </li>
          )
        })}
      </ul>
      <div className="flex items-center justify-between gap-3 border-t border-line bg-sunken/50 px-5 py-3">
        <p className="text-[13px] text-muted">{dirty ? 'You have unsaved changes.' : 'All changes saved.'}</p>
        <Button busy={save.isPending} disabled={!dirty || hasErrors} onClick={submit}>
          Save hours
        </Button>
      </div>
      {save.error && (
        <div className="px-5 pb-4">
          <Alert>{save.error.message}</Alert>
        </div>
      )}
    </Card>
  )
}

/* ------------------------------------------------------------------ Services */

function ServicesTab({ branch }: { branch: BranchDetail }) {
  if (branch.services.length === 0) {
    return (
      <EmptyState
        icon={Scissors}
        title="No services in your catalogue yet"
        action={
          <Link to="/services" className="text-sm font-medium text-accent hover:underline">
            Create services
          </Link>
        }
      >
        Create services once, then choose which branches offer them.
      </EmptyState>
    )
  }
  const categories = [...new Set(branch.services.map((s) => s.categoryName))]
  return (
    <div className="flex flex-col gap-6">
      {categories.map((category) => (
        <Card key={category} title={category} padded={false}>
          <ul className="divide-y divide-line">
            {branch.services
              .filter((s) => s.categoryName === category)
              .map((s) => (
                <OfferingRow key={s.serviceId} branchId={branch.id} service={s} />
              ))}
          </ul>
        </Card>
      ))}
    </div>
  )
}

function OfferingRow({ branchId, service }: { branchId: string; service: BranchService }) {
  const setOffering = useSetOffering(branchId)
  const toast = useToast()
  const [price, setPrice] = useState(service.priceCents === null ? '' : String(service.priceCents / 100))
  const [duration, setDuration] = useState(service.durationMin === null ? '' : String(service.durationMin))
  const changed =
    price !== (service.priceCents === null ? '' : String(service.priceCents / 100)) ||
    duration !== (service.durationMin === null ? '' : String(service.durationMin))

  return (
    <li className="flex flex-col gap-3 px-5 py-4">
      <div className="flex items-center justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-medium">{service.name}</p>
          <p className="text-[13px] text-muted">
            {formatDuration(service.durationMin ?? service.defaultDurationMin)} ·{' '}
            {formatMoney(service.priceCents ?? service.defaultPriceCents)}
            {(service.priceCents !== null || service.durationMin !== null) && ' · branch price'}
          </p>
        </div>
        <Toggle
          label={`Offer ${service.name} at this branch`}
          hideLabel
          checked={service.offered}
          disabled={setOffering.isPending}
          onChange={(offered) =>
            setOffering.mutate(
              { serviceId: service.serviceId, offered },
              { onSuccess: () => toast(offered ? `${service.name} offered here` : `${service.name} removed`) },
            )
          }
        />
      </div>
      {service.offered && (
        <div className="flex flex-wrap items-end gap-3">
          <TextField
            label="Branch price"
            prefix="$"
            inputMode="decimal"
            placeholder={String(service.defaultPriceCents / 100)}
            className="w-32"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
          />
          <TextField
            label="Duration (min)"
            inputMode="numeric"
            placeholder={String(service.defaultDurationMin)}
            className="w-32"
            value={duration}
            onChange={(e) => setDuration(e.target.value)}
          />
          {changed && (
            <Button
              size="sm"
              className="mb-1"
              busy={setOffering.isPending}
              onClick={() =>
                setOffering.mutate(
                  {
                    serviceId: service.serviceId,
                    offered: true,
                    priceCents: price ? Math.round(Number.parseFloat(price) * 100) : null,
                    durationMin: duration ? Number.parseInt(duration, 10) : null,
                  },
                  { onSuccess: () => toast('Branch price saved') },
                )
              }
            >
              Save
            </Button>
          )}
        </div>
      )}
    </li>
  )
}

/* ------------------------------------------------------------------ Closures */

function ClosuresTab({ branch }: { branch: BranchDetail }) {
  const add = useAddClosure(branch.id)
  const remove = useRemoveClosure(branch.id)
  const toast = useToast()
  const [startsOn, setStartsOn] = useState('')
  const [endsOn, setEndsOn] = useState('')
  const [reason, setReason] = useState('')

  function submit(event: FormEvent) {
    event.preventDefault()
    add.mutate(
      { startsOn, endsOn: endsOn || startsOn, reason: reason || undefined },
      {
        onSuccess: () => {
          setStartsOn('')
          setEndsOn('')
          setReason('')
          toast('Closure added')
        },
      },
    )
  }

  return (
    <div className="grid gap-6 lg:grid-cols-5">
      <Card title="Upcoming closures" description="Public holidays, renovations or anything else." className="lg:col-span-3" padded={false}>
        {branch.closures.length === 0 ? (
          <div className="p-5">
            <EmptyState icon={CalendarOff} title="No closures planned" />
          </div>
        ) : (
          <ul className="divide-y divide-line">
            {branch.closures.map((c) => (
              <li key={c.id} className="flex items-center justify-between gap-4 px-5 py-3.5">
                <div>
                  <p className="text-sm font-medium">
                    {formatDay(c.startsOn)}
                    {c.endsOn !== c.startsOn && ` – ${formatDay(c.endsOn)}`}
                  </p>
                  {c.reason && <p className="text-[13px] text-muted">{c.reason}</p>}
                </div>
                <ConfirmButton
                  label={`Remove closure starting ${formatDay(c.startsOn)}`}
                  question="Reopen these days?"
                  confirmLabel="Remove"
                  icon={Trash2}
                  iconOnly
                  busy={remove.isPending && remove.variables === c.id}
                  onConfirm={() => remove.mutate(c.id, { onSuccess: () => toast('Closure removed') })}
                />
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card title="Add a closure" className="lg:col-span-2">
        <form onSubmit={submit} className="flex flex-col gap-4">
          {add.error && <Alert>{add.error.message}</Alert>}
          <div className="grid grid-cols-2 gap-3">
            <TextField label="First day" type="date" required value={startsOn} onChange={(e) => setStartsOn(e.target.value)} />
            <TextField label="Last day" type="date" min={startsOn} value={endsOn} onChange={(e) => setEndsOn(e.target.value)} />
          </div>
          <TextField label="Reason" optional placeholder="Christmas Day" value={reason} onChange={(e) => setReason(e.target.value)} />
          <div>
            <Button type="submit" icon={Plus} busy={add.isPending} disabled={!startsOn}>
              Add closure
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}

/* ------------------------------------------------------------------- Details */

function DetailsTab({ branch }: { branch: BranchDetail }) {
  const update = useUpdateBranch(branch.id)
  const toast = useToast()
  const isOwner = useMe().data?.selected?.role === 'OWNER'
  const slugTaken = update.error instanceof ApiError && update.error.code === 'SLUG_TAKEN'

  const overview = useOverview()
  if (!isOwner) {
    return <Alert tone="info">Only owners can change branch details.</Alert>
  }
  return (
    <div className="flex flex-col gap-6">
      {overview.data && <BranchRulesCard key={JSON.stringify(branch.policy)} branch={branch} business={overview.data.policy} />}
      <Card>
        {update.error && !slugTaken && (
          <div className="mb-4">
            <Alert>{update.error.message}</Alert>
          </div>
        )}
        <BranchForm
          id="branch-details"
          withActiveToggle
          initial={{ ...branch, addressLine2: branch.addressLine2 ?? '', phone: branch.phone ?? '', email: branch.email ?? '' }}
          slugError={slugTaken ? update.error?.message : undefined}
          onSubmit={(input) => update.mutate(input, { onSuccess: () => toast('Branch details saved') })}
        />
        <div className="mt-6 border-t border-line pt-4">
          <Button type="submit" form="branch-details" busy={update.isPending}>
            Save details
          </Button>
        </div>
      </Card>
    </div>
  )
}
