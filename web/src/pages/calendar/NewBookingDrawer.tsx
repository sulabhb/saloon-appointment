import { Search, UserPlus, X } from 'lucide-react'
import { useState } from 'react'
import { useBookingOptions, useCreateStaffBooking, useCustomerSearch } from '../../api/bookingQueries'
import type { CustomerMatch } from '../../api/bookingTypes'
import { Drawer } from '../../components/Drawer'
import { useToast } from '../../components/Toast'
import { Alert, Avatar, Button, CheckboxList, Skeleton, TextAreaField, TextField, formatDuration, formatMoney } from '../../components/ui'
import { SlotPicker } from './SlotPicker'
import { branchClock } from './time'

export interface NewBookingStart {
  locationId: string
  date: string
  staffId?: string | null
  walkIn?: boolean
}

/**
 * A booking made by staff (DSH-9): customer, services, stylist and time, then book. A walk-in
 * (DSH-10) is the same on today's date, starts checked in and needs only a name.
 */
export function NewBookingDrawer({ start, onClose, onBooked }: { start: NewBookingStart; onClose: () => void; onBooked: (id: string) => void }) {
  const walkIn = Boolean(start.walkIn)
  const options = useBookingOptions(start.locationId)
  const create = useCreateStaffBooking()
  const toast = useToast()

  const [customer, setCustomer] = useState<CustomerMatch | 'new' | null>(walkIn ? 'new' : null)
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [serviceIds, setServiceIds] = useState<string[]>([])
  const [staffId, setStaffId] = useState<string | null>(start.staffId ?? null)
  const [date, setDate] = useState(start.date)
  const [slot, setSlot] = useState<string | null>(null)
  const [internalNote, setInternalNote] = useState('')
  const [key] = useState(() => crypto.randomUUID())

  const services = (options.data?.categories ?? []).flatMap((c) =>
    c.services.map((s) => ({ ...s, label: s.name, detail: `${c.name}, ${formatDuration(s.durationMin)}, ${s.priceIsFrom ? 'from ' : ''}${formatMoney(s.priceCents)}` })),
  )
  const chosen = services.filter((s) => serviceIds.includes(s.id))
  const newCustomerOk = customer === 'new' && fullName.trim() && (walkIn || phone.trim() || email.trim())
  const ready = (customer !== null && customer !== 'new' ? true : Boolean(newCustomerOk)) && serviceIds.length > 0 && slot

  function submit() {
    if (!ready || !slot) return
    create.mutate(
      {
        locationId: start.locationId,
        serviceIds,
        staffId,
        start: slot,
        ...(customer !== null && customer !== 'new'
          ? { customerId: customer.id }
          : { customer: { fullName: fullName.trim(), phone: phone.trim() || undefined, email: email.trim() || undefined } }),
        internalNote: internalNote.trim() || undefined,
        walkIn,
        idempotencyKey: key,
      },
      {
        onSuccess: (booked) => {
          toast(`${walkIn ? 'Walk-in checked in' : 'Booked'}: ${booked.customer.fullName}, ${branchClock(booked.startAt, booked.timeZone)} with ${booked.staffName}`)
          onBooked(booked.id)
        },
      },
    )
  }

  return (
    <Drawer
      open
      onClose={onClose}
      title={walkIn ? 'Walk-in' : 'New booking'}
      description={walkIn ? 'Starts checked in. Contact details are optional.' : 'Book for a customer on the phone or at the desk.'}
      footer={
        <>
          <p className="mr-auto truncate text-[13px] text-muted tabular-nums">
            {chosen.length > 0 &&
              `${formatDuration(chosen.reduce((m, s) => m + s.durationMin, 0))}, ${formatMoney(chosen.reduce((c, s) => c + s.priceCents, 0))}`}
          </p>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={!ready} busy={create.isPending} onClick={submit}>
            {walkIn ? 'Check in' : 'Book'}
          </Button>
        </>
      }
    >
      {options.isPending && <Skeleton lines={4} />}
      {options.error && <Alert>{options.error.message}</Alert>}
      {options.data && (
        <div className="flex flex-col gap-7">
          <section aria-labelledby="nb-customer" className="flex flex-col gap-3">
            <h3 id="nb-customer" className="text-sm font-semibold">
              Customer
            </h3>
            {customer === null && <CustomerPicker onPick={setCustomer} />}
            {customer !== null && customer !== 'new' && (
              <div className="flex items-center gap-3 rounded-lg border border-line px-3 py-2.5">
                <Avatar name={customer.fullName} size="sm" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{customer.fullName}</p>
                  <p className="truncate text-[13px] text-muted">{[customer.phone, customer.email].filter(Boolean).join(', ')}</p>
                </div>
                <button type="button" aria-label="Choose another customer" onClick={() => setCustomer(null)} className="rounded-lg p-1.5 text-muted hover:bg-sunken hover:text-ink">
                  <X className="size-4" aria-hidden />
                </button>
              </div>
            )}
            {customer === 'new' && (
              <div className="flex flex-col gap-3">
                <TextField label="Name" autoComplete="off" value={fullName} onChange={(e) => setFullName(e.target.value)} />
                <div className="grid grid-cols-2 gap-3">
                  <TextField label="Mobile" type="tel" optional={walkIn} value={phone} onChange={(e) => setPhone(e.target.value)} />
                  <TextField label="Email" type="email" optional value={email} onChange={(e) => setEmail(e.target.value)} />
                </div>
                {!walkIn && <p className="text-[13px] text-muted">A mobile or email is needed so the salon can reach them.</p>}
                {!walkIn && (
                  <div>
                    <Button variant="ghost" size="sm" icon={Search} onClick={() => setCustomer(null)}>
                      Search existing customers instead
                    </Button>
                  </div>
                )}
              </div>
            )}
          </section>

          <CheckboxList
            legend="Services"
            items={services}
            selected={serviceIds}
            onChange={(ids) => {
              setServiceIds(ids)
              setSlot(null)
            }}
            empty="This branch has no services yet."
          />

          <section aria-labelledby="nb-time" className="flex flex-col gap-3">
            <h3 id="nb-time" className="text-sm font-semibold">
              Stylist and time
            </h3>
            <SlotPicker
              locationId={start.locationId}
              serviceIds={serviceIds}
              date={date}
              onDate={(d) => {
                setDate(d)
                setSlot(null)
              }}
              staffId={staffId}
              onStaff={(id) => {
                setStaffId(id)
                setSlot(null)
              }}
              start={slot}
              onStart={setSlot}
              lockDate={walkIn}
            />
          </section>

          <TextAreaField label="Staff note" optional hint="Only staff see this." maxLength={2000} value={internalNote} onChange={(e) => setInternalNote(e.target.value)} />
          {create.error && <Alert>{create.error.message}</Alert>}
        </div>
      )}
    </Drawer>
  )
}

/** Find a customer by name, mobile, email or booking reference (DSH-13), or start a new one. */
function CustomerPicker({ onPick }: { onPick: (customer: CustomerMatch | 'new') => void }) {
  const [q, setQ] = useState('')
  const results = useCustomerSearch(q)
  return (
    <div className="flex flex-col gap-2">
      <TextField label="Search" hint="Name, mobile, email or booking reference" value={q} onChange={(e) => setQ(e.target.value)} autoComplete="off" />
      {q.trim().length >= 2 && results.data && (
        <ul className="max-h-56 divide-y divide-line overflow-y-auto rounded-lg border border-line" aria-label="Matching customers">
          {results.data.length === 0 && <li className="px-3 py-2.5 text-sm text-muted">No customer matches “{q.trim()}”.</li>}
          {results.data.map((c) => (
            <li key={c.id}>
              <button type="button" onClick={() => onPick(c)} className="flex w-full items-center gap-3 px-3 py-2.5 text-left hover:bg-sunken">
                <Avatar name={c.fullName} size="sm" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{c.fullName}</span>
                  <span className="block truncate text-[13px] text-muted">{[c.phone, c.email].filter(Boolean).join(', ')}</span>
                </span>
                {c.upcoming > 0 && <span className="text-[13px] text-muted">{c.upcoming} upcoming</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
      <div>
        <Button variant="secondary" size="sm" icon={UserPlus} onClick={() => onPick('new')}>
          New customer
        </Button>
      </div>
    </div>
  )
}
