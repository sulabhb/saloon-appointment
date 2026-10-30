import { Check, Clock3 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { formatDuration, formatMoney } from '../components/ui'
import {
  useBranchCatalog,
  useCreateBooking,
  fetchDays,
  fetchSlots,
  usePublicBusiness,
  type BookingView,
  type BranchCatalog,
  type PublicBranch,
  type PublicBusiness,
  type ServiceOption,
} from './api'
import { ActionBar, BookingFrame, Notice, SalonHeader, StepHeading } from './BookingFrame'
import { Confirmation } from './Confirmation'
import { TimePicker } from './TimePicker'
import { branchLabel, clock, cutoffInWords, fullDate, shortDate } from './format'

type Step = 'branch' | 'services' | 'stylist' | 'time' | 'details' | 'review'

const STEPS: Step[] = ['services', 'stylist', 'time', 'details', 'review']

interface Draft {
  step: Step
  branch: string | null
  serviceIds: string[]
  staffId: string | null
  date: string | null
  start: string | null
  fullName: string
  email: string
  phone: string
  note: string
  acceptsPolicy: boolean
  /** Made when the review opens, so a double tap or retry books once (BKG-10). */
  idempotencyKey: string | null
}

const emptyDraft = (branch: string | null): Draft => ({
  step: branch ? 'services' : 'branch',
  branch,
  serviceIds: [],
  staffId: null,
  date: null,
  start: null,
  fullName: '',
  email: '',
  phone: '',
  note: '',
  acceptsPolicy: false,
  idempotencyKey: null,
})

/** Refreshing keeps the booking in progress for this browser tab (BKG-17). */
function useSessionDraft(business: string, routeBranch: string | undefined) {
  const key = `booking:${business}`
  const [draft, setDraft] = useState<Draft>(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem(key) ?? 'null') as Draft | null
      if (saved && (!routeBranch || saved.branch === routeBranch)) return saved
    } catch {
      // Storage unavailable (private mode): start fresh.
    }
    return emptyDraft(routeBranch ?? null)
  })
  useEffect(() => {
    try {
      sessionStorage.setItem(key, JSON.stringify(draft))
    } catch {
      // Not critical: the booking still works without it.
    }
  }, [key, draft])
  const clear = () => {
    try {
      sessionStorage.removeItem(key)
    } catch {
      // ignore
    }
  }
  return [draft, setDraft, clear] as const
}


export function BookingPage() {
  const { business: slug = '', branch: routeBranch } = useParams()
  const business = usePublicBusiness(slug)

  if (business.isPending) {
    return (
      <BookingFrame accentColor={null}>
        <div className="mx-auto max-w-[34rem] px-5 pt-24" role="status" aria-label="Loading">
          <div className="h-10 w-2/3 animate-pulse rounded-lg bg-[#F3F2F0]" />
          <div className="mt-4 h-5 w-1/3 animate-pulse rounded bg-[#F3F2F0]" />
        </div>
      </BookingFrame>
    )
  }
  if (business.error) {
    const unavailable = business.error instanceof ApiError && business.error.code === 'BUSINESS_UNAVAILABLE'
    return (
      <BookingFrame accentColor={null}>
        <main className="mx-auto max-w-[34rem] px-5 pt-24">
          <h1 className="text-2xl font-semibold">{unavailable ? 'Online booking is paused' : 'Salon not found'}</h1>
          <p className="mt-2 text-[#6E6A65]">
            {unavailable
              ? 'This salon is not taking online bookings right now. Please call them to book.'
              : 'Check the link or salon code you were given.'}
          </p>
        </main>
      </BookingFrame>
    )
  }
  return <BookingFlow business={business.data} routeBranch={routeBranch} />
}

function BookingFlow({ business, routeBranch }: { business: PublicBusiness; routeBranch?: string }) {
  const only = business.locations.length === 1 ? business.locations[0]!.slug : undefined
  const [draft, setDraft, clearDraft] = useSessionDraft(business.slug, routeBranch ?? only)
  const [booked, setBooked] = useState<{ booking: BookingView; manageToken: string } | null>(null)
  const [timeGone, setTimeGone] = useState(false)
  const branch = business.locations.find((b) => b.slug === draft.branch)
  const catalog = useBranchCatalog(business.slug, branch?.slug)
  const navigate = useNavigate()

  const update = (change: Partial<Draft>) => setDraft((d) => ({ ...d, ...change }))
  const go = (step: Step) => {
    update({ step })
    window.scrollTo({ top: 0 })
  }

  const services = useMemo(() => {
    const all = (catalog.data?.categories ?? []).flatMap((c) => c.services)
    return draft.serviceIds.map((id) => all.find((s) => s.id === id)).filter((s): s is ServiceOption => Boolean(s))
  }, [catalog.data, draft.serviceIds])

  if (booked) {
    return (
      <BookingFrame accentColor={business.accentColor}>
        <Confirmation booking={booked.booking} manageToken={booked.manageToken} onBookAnother={() => setBooked(null)} />
      </BookingFrame>
    )
  }

  const stepIndex = STEPS.indexOf(draft.step)
  const back =
    draft.step === 'branch' || (draft.step === 'services' && (only || routeBranch))
      ? undefined
      : () => go(stepIndex <= 0 ? 'branch' : STEPS[stepIndex - 1]!)

  return (
    <BookingFrame accentColor={business.accentColor}>
      <SalonHeader
        name={business.displayName}
        branchLine={branch ? branchLabel(branch) : business.description ?? undefined}
        onChangeBranch={branch && business.locations.length > 1 && draft.step !== 'branch' ? () => go('branch') : undefined}
        onBack={back}
      />
      <main className="mx-auto max-w-[34rem] px-5 pb-10">
        {draft.step === 'branch' && (
          <BranchStep
            branches={business.locations}
            selected={draft.branch}
            onSelect={(slug) => {
              // Services differ by branch, so a new branch starts the choices again.
              setDraft((d) => (d.branch === slug ? { ...d, step: 'services' } : { ...emptyDraft(slug), fullName: d.fullName, email: d.email, phone: d.phone }))
              navigate(`/book/${business.slug}/${slug}`, { replace: true })
            }}
          />
        )}
        {branch && draft.step !== 'branch' && catalog.isPending && <ListSkeleton />}
        {branch && catalog.error && <Notice tone="error">{catalog.error.message}</Notice>}
        {branch && catalog.data && (
          <>
            {draft.step === 'services' && (
              <ServicesStep
                catalog={catalog.data}
                selected={draft.serviceIds}
                onToggle={(id) =>
                  update({
                    serviceIds: draft.serviceIds.includes(id) ? draft.serviceIds.filter((s) => s !== id) : [...draft.serviceIds, id],
                    date: null,
                    start: null,
                  })
                }
                onContinue={() => go('stylist')}
                services={services}
              />
            )}
            {draft.step === 'stylist' && (
              <StylistStep
                catalog={catalog.data}
                serviceIds={draft.serviceIds}
                selected={draft.staffId}
                onSelect={(staffId) => update({ staffId, start: null })}
                onContinue={() => go('time')}
                services={services}
              />
            )}
            {draft.step === 'time' && (
              <TimeStep
                business={business.slug}
                branch={branch}
                draft={draft}
                timeGone={timeGone}
                onDate={(date) => update({ date, start: null })}
                onStart={(start) => {
                  setTimeGone(false)
                  update({ start })
                }}
                onContinue={() => go('details')}
                services={services}
              />
            )}
            {draft.step === 'details' && (
              <DetailsStep
                business={business}
                draft={draft}
                onChange={update}
                onContinue={() => {
                  update({ idempotencyKey: draft.idempotencyKey ?? crypto.randomUUID() })
                  go('review')
                }}
                services={services}
              />
            )}
            {draft.step === 'review' && (
              <ReviewStep
                business={business}
                branch={branch}
                draft={draft}
                catalog={catalog.data}
                services={services}
                onEdit={go}
                onBooked={(result) => {
                  clearDraft()
                  setDraft(emptyDraft(draft.branch))
                  setBooked(result)
                  window.scrollTo({ top: 0 })
                }}
                onTimeGone={() => {
                  setTimeGone(true)
                  update({ start: null, idempotencyKey: null })
                  go('time')
                }}
              />
            )}
          </>
        )}
      </main>
    </BookingFrame>
  )
}

/* ------------------------------------------------------------------ Pieces */

function totals(services: ServiceOption[]) {
  const minutes = services.reduce((sum, s) => sum + s.durationMin, 0)
  const cents = services.reduce((sum, s) => sum + s.priceCents, 0)
  const from = services.some((s) => s.priceIsFrom)
  return {
    summary: `${services.length} ${services.length === 1 ? 'service' : 'services'}, ${formatDuration(minutes)}`,
    price: `${from ? 'From ' : ''}${formatMoney(cents)}`,
  }
}

function ListSkeleton() {
  return (
    <div role="status" aria-label="Loading" className="flex flex-col gap-3">
      {[0, 1, 2].map((i) => (
        <div key={i} className="h-16 animate-pulse rounded-xl bg-[#F3F2F0]" />
      ))}
    </div>
  )
}

/** A full-width choice row: radio or checkbox semantics, the salon's colour when chosen. */
function ChoiceRow({
  role,
  checked,
  onClick,
  children,
  aside,
}: {
  role: 'radio' | 'checkbox'
  checked: boolean
  onClick: () => void
  children: React.ReactNode
  aside?: React.ReactNode
}) {
  return (
    <button
      type="button"
      role={role}
      aria-checked={checked}
      onClick={onClick}
      className="flex w-full items-start gap-3.5 py-4 text-left transition-colors duration-150 ease-out active:bg-[#F7F6F4]"
    >
      <span
        aria-hidden
        className={`mt-0.5 flex size-[22px] shrink-0 items-center justify-center border-2 transition-colors duration-150 ease-out ${
          role === 'radio' ? 'rounded-full' : 'rounded-md'
        } ${checked ? 'border-(--brand) bg-(--brand) text-(--on-brand)' : 'border-[#CFCBC6] bg-white'}`}
      >
        {checked && <Check className="size-3.5" strokeWidth={3} />}
      </span>
      <span className="min-w-0 flex-1">{children}</span>
      {aside && <span className="shrink-0 text-right tabular-nums">{aside}</span>}
    </button>
  )
}

function BranchStep({ branches, selected, onSelect }: { branches: PublicBranch[]; selected: string | null; onSelect: (slug: string) => void }) {
  return (
    <>
      <h1 className="mb-3 text-[1.375rem] font-semibold">Choose a branch</h1>
      <div role="radiogroup" aria-label="Branch" className="divide-y divide-[#E6E4E1] border-y border-[#E6E4E1]">
        {branches.map((b) => (
          <ChoiceRow key={b.slug} role="radio" checked={selected === b.slug} onClick={() => onSelect(b.slug)}>
            <span className="block font-semibold">{b.name}</span>
            <span className="block text-sm text-[#6E6A65]">
              {b.suburb}, {b.state}
            </span>
            {b.phone && <span className="block text-sm text-[#6E6A65] tabular-nums">{b.phone}</span>}
          </ChoiceRow>
        ))}
      </div>
    </>
  )
}

function ServicesStep({
  catalog,
  selected,
  onToggle,
  onContinue,
  services,
}: {
  catalog: BranchCatalog
  selected: string[]
  onToggle: (id: string) => void
  onContinue: () => void
  services: ServiceOption[]
}) {
  const t = totals(services)
  const { business } = useParams()
  if (catalog.categories.length === 0) {
    return <Notice>This branch has no services to book online yet. Please call the salon.</Notice>
  }
  return (
    <>
      <StepHeading step={1} of={STEPS.length} title="What would you like?" />
      <div className="flex flex-col gap-7">
        {catalog.categories.map((category) => (
          <section key={category.name} aria-labelledby={`cat-${category.name}`}>
            <h2 id={`cat-${category.name}`} className="mb-1 font-semibold text-[#6E6A65]">
              {category.name}
            </h2>
            <div className="divide-y divide-[#E6E4E1] border-y border-[#E6E4E1]">
              {category.services.map((s) => {
                const checked = selected.includes(s.id)
                return (
                  <div key={s.id}>
                    <ChoiceRow
                      role="checkbox"
                      checked={checked}
                      onClick={() => onToggle(s.id)}
                      aside={
                        <>
                          <span className="block font-semibold">
                            {s.priceIsFrom && <span className="font-normal text-[#6E6A65]">from </span>}
                            {formatMoney(s.priceCents)}
                          </span>
                          <span className="block text-sm text-[#6E6A65]">{formatDuration(s.durationMin)}</span>
                        </>
                      }
                    >
                      <span className="block font-semibold">{s.name}</span>
                      {s.description && <span className="mt-0.5 block text-sm text-pretty text-[#6E6A65]">{s.description}</span>}
                    </ChoiceRow>
                    {checked && s.customerNotice && (
                      <p className="-mt-1 mb-4 ml-9 rounded-lg bg-[#F3F2F0] px-3 py-2 text-sm">{s.customerNotice}</p>
                    )}
                  </div>
                )
              })}
            </div>
          </section>
        ))}
      </div>
      <p className="mt-8 text-sm text-[#6E6A65]">
        Already booked?{' '}
        <Link to={`/book/${business}/find`} className="font-medium text-[#1C1B1A] underline underline-offset-4">
          Find your booking
        </Link>
      </p>
      {services.length > 0 && <ActionBar summary={t.summary} detail={t.price} action="Continue" onAction={onContinue} />}
    </>
  )
}

function StylistStep({
  catalog,
  serviceIds,
  selected,
  onSelect,
  onContinue,
  services,
}: {
  catalog: BranchCatalog
  serviceIds: string[]
  selected: string | null
  onSelect: (id: string | null) => void
  onContinue: () => void
  services: ServiceOption[]
}) {
  const qualified = catalog.stylists.filter((s) => serviceIds.every((id) => s.serviceIds.includes(id)))
  const t = totals(services)
  if (qualified.length === 0) {
    return (
      <>
        <StepHeading step={2} of={STEPS.length} title="Who would you like?" />
        <Notice>No one here does all of these in one visit. Go back and remove a service, or call the salon to book them together.</Notice>
      </>
    )
  }
  return (
    <>
      <StepHeading step={2} of={STEPS.length} title="Who would you like?" />
      <div role="radiogroup" aria-label="Stylist" className="divide-y divide-[#E6E4E1] border-y border-[#E6E4E1]">
        <ChoiceRow role="radio" checked={selected === null} onClick={() => onSelect(null)}>
          <span className="block font-semibold">Anyone available</span>
          <span className="block text-sm text-[#6E6A65]">Shows the most times</span>
        </ChoiceRow>
        {qualified.map((s) => (
          <ChoiceRow key={s.id} role="radio" checked={selected === s.id} onClick={() => onSelect(s.id)}>
            <span className="block font-semibold">{s.displayName}</span>
            {s.bio && <span className="block text-sm text-pretty text-[#6E6A65]">{s.bio}</span>}
          </ChoiceRow>
        ))}
      </div>
      <ActionBar summary={t.summary} detail={t.price} action="Choose a time" onAction={onContinue} />
    </>
  )
}

function TimeStep({
  business,
  branch,
  draft,
  timeGone,
  onDate,
  onStart,
  onContinue,
  services,
}: {
  business: string
  branch: PublicBranch
  draft: Draft
  timeGone: boolean
  onDate: (date: string) => void
  onStart: (start: string) => void
  onContinue: () => void
  services: ServiceOption[]
}) {
  const t = totals(services)
  return (
    <>
      <StepHeading step={3} of={STEPS.length} title="When suits you?" />
      {timeGone && (
        <div className="mb-4">
          <Notice tone="error">That time was just booked by someone else. Your other choices are kept; pick another time.</Notice>
        </div>
      )}
      <TimePicker
        timeZone={branch.timeZone}
        date={draft.date}
        start={draft.start}
        onDate={onDate}
        onStart={onStart}
        withStylist={draft.staffId !== null}
        cacheKey={['public', business, branch.slug, draft.serviceIds, draft.staffId]}
        fetchDays={(from, to) => fetchDays(business, branch.slug, draft.serviceIds, draft.staffId, from, to)}
        fetchSlots={(date) => fetchSlots(business, branch.slug, draft.serviceIds, draft.staffId, date)}
      />
      {draft.start ? (
        <ActionBar
          summary={`${shortDate(draft.start, branch.timeZone)}, ${clock(draft.start, branch.timeZone)}`}
          detail={`${t.summary}. ${t.price}`}
          action="Continue"
          onAction={onContinue}
        />
      ) : (
        services.length > 0 && <ActionBar summary={t.summary} detail="Choose a time" action="Continue" onAction={onContinue} disabled />
      )}
    </>
  )
}

const inputStyle =
  'h-12 w-full rounded-xl border border-[#CFCBC6] bg-white px-4 text-base outline-none focus:border-(--brand) focus:ring-2 focus:ring-(--brand)/25 aria-invalid:border-[#B3261E]'

function Field({ id, label, hint, error, children }: { id: string; label: string; hint?: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="font-semibold">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-sm text-[#B3261E]">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="text-sm text-[#6E6A65]">
            {hint}
          </p>
        )
      )}
    </div>
  )
}

const emailOk = (email: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
const phoneOk = (phone: string) => /^\+?[0-9][0-9\s().-]{7,}$/.test(phone.trim())

function DetailsStep({
  business,
  draft,
  onChange,
  onContinue,
  services,
}: {
  business: PublicBusiness
  draft: Draft
  onChange: (change: Partial<Draft>) => void
  onContinue: () => void
  services: ServiceOption[]
}) {
  const [touched, setTouched] = useState(false)
  const errors = {
    fullName: draft.fullName.trim() ? undefined : 'Enter your name.',
    email: emailOk(draft.email) ? undefined : 'Enter an email address, like name@example.com.',
    phone: phoneOk(draft.phone) ? undefined : 'Enter a mobile number, like 0412 345 678.',
    acceptsPolicy: draft.acceptsPolicy ? undefined : 'Agree to the cancellation policy to continue.',
  }
  const valid = !Object.values(errors).some(Boolean)
  const show = (key: keyof typeof errors) => (touched ? errors[key] : undefined)
  const t = totals(services)

  return (
    <>
      <StepHeading step={4} of={STEPS.length} title="Your details" />
      <form
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          setTouched(true)
          if (valid) onContinue()
        }}
        className="flex flex-col gap-5"
      >
        <Field id="fullName" label="Name" error={show('fullName')}>
          <input
            id="fullName"
            autoComplete="name"
            value={draft.fullName}
            onChange={(e) => onChange({ fullName: e.target.value })}
            aria-invalid={Boolean(show('fullName'))}
            aria-describedby={show('fullName') ? 'fullName-error' : undefined}
            className={inputStyle}
          />
        </Field>
        <Field id="email" label="Email" hint="For your booking details." error={show('email')}>
          <input
            id="email"
            type="email"
            autoComplete="email"
            inputMode="email"
            value={draft.email}
            onChange={(e) => onChange({ email: e.target.value })}
            aria-invalid={Boolean(show('email'))}
            aria-describedby={show('email') ? 'email-error' : 'email-hint'}
            className={inputStyle}
          />
        </Field>
        <Field id="phone" label="Mobile" hint="So the salon can reach you about this booking." error={show('phone')}>
          <input
            id="phone"
            type="tel"
            autoComplete="tel"
            inputMode="tel"
            value={draft.phone}
            onChange={(e) => onChange({ phone: e.target.value })}
            aria-invalid={Boolean(show('phone'))}
            aria-describedby={show('phone') ? 'phone-error' : 'phone-hint'}
            className={inputStyle}
          />
        </Field>
        <Field id="note" label="Note for the salon (optional)" hint={`${500 - draft.note.length} characters left`}>
          <textarea
            id="note"
            rows={3}
            maxLength={500}
            value={draft.note}
            onChange={(e) => onChange({ note: e.target.value })}
            aria-describedby="note-hint"
            className={`${inputStyle} h-auto py-3`}
          />
        </Field>
        <div>
          <label className="flex items-start gap-3">
            <input
              type="checkbox"
              checked={draft.acceptsPolicy}
              onChange={(e) => onChange({ acceptsPolicy: e.target.checked })}
              aria-invalid={Boolean(show('acceptsPolicy'))}
              aria-describedby="policy-text"
              className="mt-0.5 size-[22px] shrink-0 accent-(--brand)"
            />
            <span id="policy-text">
              I agree to the cancellation policy: cancel online up to {cutoffInWords(business.cancelCutoffMin)} before the
              appointment. After that, call the salon.
            </span>
          </label>
          {show('acceptsPolicy') && <p className="mt-1.5 ml-[34px] text-sm text-[#B3261E]">{show('acceptsPolicy')}</p>}
        </div>
        <p className="text-sm text-pretty text-[#6E6A65]">
          {business.displayName} uses your details only to manage this booking and contact you about it.
        </p>
        <button type="submit" className="sr-only">
          Review booking
        </button>
      </form>
      <ActionBar
        summary={t.summary}
        detail={t.price}
        action="Review"
        onAction={() => {
          setTouched(true)
          if (valid) onContinue()
        }}
      />
    </>
  )
}

function ReviewStep({
  business,
  branch,
  draft,
  catalog,
  services,
  onEdit,
  onBooked,
  onTimeGone,
}: {
  business: PublicBusiness
  branch: PublicBranch
  draft: Draft
  catalog: BranchCatalog
  services: ServiceOption[]
  onEdit: (step: Step) => void
  onBooked: (result: { booking: BookingView; manageToken: string }) => void
  onTimeGone: () => void
}) {
  const create = useCreateBooking(business.slug)
  const t = totals(services)
  const minutes = services.reduce((sum, s) => sum + s.durationMin, 0)
  const end = draft.start ? new Date(new Date(draft.start).getTime() + minutes * 60_000).toISOString() : null
  const stylist = catalog.stylists.find((s) => s.id === draft.staffId)?.displayName ?? 'Anyone available'

  if (!draft.start || !end) {
    return <Notice>Choose a time first.</Notice>
  }

  function confirm() {
    create.mutate(
      {
        locationSlug: branch.slug,
        serviceIds: draft.serviceIds,
        staffId: draft.staffId,
        start: draft.start!,
        customer: { fullName: draft.fullName.trim(), email: draft.email.trim(), phone: draft.phone.trim() },
        note: draft.note.trim() || undefined,
        acceptsPolicy: draft.acceptsPolicy,
        idempotencyKey: draft.idempotencyKey ?? crypto.randomUUID(),
      },
      {
        onSuccess: onBooked,
        onError: (error) => {
          if (error instanceof ApiError && error.code === 'SLOT_UNAVAILABLE') onTimeGone()
        },
      },
    )
  }

  const rows: { label: string; value: React.ReactNode; edit?: Step }[] = [
    {
      label: 'When',
      value: (
        <>
          {fullDate(draft.start, branch.timeZone)}
          <br />
          <span className="tabular-nums">
            {clock(draft.start, branch.timeZone)} to about {clock(end, branch.timeZone)}
          </span>
        </>
      ),
      edit: 'time',
    },
    { label: 'Where', value: branchLabel(branch) },
    {
      label: 'Services',
      value: (
        <ul>
          {services.map((s) => (
            <li key={s.id} className="flex justify-between gap-4">
              <span>{s.name}</span>
              <span className="tabular-nums">
                {s.priceIsFrom && 'from '}
                {formatMoney(s.priceCents)}
              </span>
            </li>
          ))}
        </ul>
      ),
      edit: 'services',
    },
    { label: 'With', value: stylist, edit: 'stylist' },
    {
      label: 'Your details',
      value: (
        <>
          {draft.fullName}
          <br />
          {draft.email}
          <br />
          {draft.phone}
        </>
      ),
      edit: 'details',
    },
  ]

  const failed = create.error instanceof ApiError && create.error.code !== 'SLOT_UNAVAILABLE' ? create.error : null

  return (
    <>
      <StepHeading step={5} of={STEPS.length} title="Check and confirm" />
      <dl className="divide-y divide-[#E6E4E1] border-y border-[#E6E4E1]">
        {rows.map((row) => (
          <div key={row.label} className="flex gap-4 py-4">
            <dt className="w-24 shrink-0 text-[#6E6A65]">{row.label}</dt>
            <dd className="min-w-0 flex-1">{row.value}</dd>
            {row.edit && (
              <button
                type="button"
                onClick={() => onEdit(row.edit!)}
                className="self-start text-sm font-medium underline underline-offset-4"
                aria-label={`Change ${row.label.toLowerCase()}`}
              >
                Change
              </button>
            )}
          </div>
        ))}
      </dl>
      <p className="mt-4 flex items-start gap-2 text-sm text-[#6E6A65]">
        <Clock3 className="mt-0.5 size-4 shrink-0" aria-hidden />
        You can cancel online up to {cutoffInWords(business.cancelCutoffMin)} before the appointment.
      </p>
      {failed && (
        <div className="mt-4">
          <Notice tone="error">{failed.message}</Notice>
        </div>
      )}
      <ActionBar
        summary={`${shortDate(draft.start, branch.timeZone)}, ${clock(draft.start, branch.timeZone)}`}
        detail={t.price}
        action="Confirm booking"
        onAction={confirm}
        busy={create.isPending}
      />
    </>
  )
}

/** Shared by the confirmation and management pages. */
export function BookAgainLink({ business }: { business: string }) {
  return (
    <Link to={`/book/${business}`} className="font-medium underline underline-offset-4">
      Book another visit
    </Link>
  )
}
