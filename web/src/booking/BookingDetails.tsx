import { MapPin, Phone } from 'lucide-react'
import { formatDuration, formatMoney } from '../components/ui'
import type { BookingView } from './api'
import { clock, fullDate } from './format'

/** What, when, where and with whom: shared by the confirmation and the management link. */
export function BookingDetails({ booking }: { booking: BookingView }) {
  const tz = booking.branch.timeZone
  const minutes = booking.services.reduce((sum, s) => sum + s.durationMin, 0)
  const from = booking.services.some((s) => s.priceIsFrom)
  const address = [booking.branch.addressLine1, booking.branch.addressLine2, `${booking.branch.suburb} ${booking.branch.state} ${booking.branch.postcode}`]
    .filter(Boolean)
    .join(', ')
  return (
    <dl className="divide-y divide-[#E6E4E1] border-y border-[#E6E4E1]">
      <Row label="When">
        {fullDate(booking.startAt, tz)}
        <br />
        <span className="tabular-nums">
          {clock(booking.startAt, tz)} to about {clock(booking.endAt, tz)}{' '}
          <span className="whitespace-nowrap">({formatDuration(minutes)})</span>
        </span>
      </Row>
      <Row label="Services">
        <ul>
          {booking.services.map((s) => (
            <li key={s.name} className="flex justify-between gap-4">
              <span>{s.name}</span>
              <span className="tabular-nums">
                {s.priceIsFrom && 'from '}
                {formatMoney(s.priceCents)}
              </span>
            </li>
          ))}
        </ul>
        {booking.services.length > 1 && (
          <p className="mt-1 flex justify-between gap-4 font-semibold">
            <span>Total</span>
            <span className="tabular-nums">
              {from && 'from '}
              {formatMoney(booking.totalPriceCents)}
            </span>
          </p>
        )}
      </Row>
      <Row label="With">{booking.staffName}</Row>
      <Row label="Where">
        <span className="font-semibold">{booking.branch.name}</span>
        <br />
        <a
          href={`https://maps.google.com/?q=${encodeURIComponent(`${booking.businessName}, ${address}`)}`}
          className="inline-flex items-start gap-1.5 underline-offset-4 hover:underline"
        >
          <MapPin className="mt-1 size-4 shrink-0 text-[#6E6A65]" aria-hidden />
          {address}
        </a>
        {booking.branch.phone && (
          <>
            <br />
            <a href={`tel:${booking.branch.phone.replace(/\s/g, '')}`} className="inline-flex items-center gap-1.5 tabular-nums underline-offset-4 hover:underline">
              <Phone className="size-4 text-[#6E6A65]" aria-hidden />
              {booking.branch.phone}
            </a>
          </>
        )}
      </Row>
      <Row label="Reference">
        <span className="font-semibold tracking-normal tabular-nums">{booking.reference}</span>
      </Row>
    </dl>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-4 py-4">
      <dt className="w-24 shrink-0 text-[#6E6A65]">{label}</dt>
      <dd className="min-w-0 flex-1">{children}</dd>
    </div>
  )
}
