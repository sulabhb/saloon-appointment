import { CalendarPlus } from 'lucide-react'
import { Link } from 'react-router'
import type { BookingView } from './api'
import { BookingDetails } from './BookingDetails'
import { SalonHeader } from './BookingFrame'
import { branchLabel, clock, fullDate, googleCalendarUrl, icsDataUrl, type CalendarEvent } from './format'

/** After booking (BKG-11): what was booked, how to add it to a calendar, and the private link to manage it. */
export function Confirmation({
  booking,
  manageToken,
  onBookAnother,
}: {
  booking: BookingView
  manageToken: string
  onBookAnother: () => void
}) {
  const tz = booking.branch.timeZone
  const manageUrl = `${window.location.origin}/m/${manageToken}`
  const event: CalendarEvent = {
    title: `${booking.services.map((s) => s.name).join(', ')} at ${booking.businessName}`,
    start: booking.startAt,
    end: booking.endAt,
    location: [booking.branch.addressLine1, booking.branch.suburb, booking.branch.state].join(', '),
    details: `With ${booking.staffName}. Reference ${booking.reference}. Change or cancel: ${manageUrl}`,
  }
  return (
    <>
      <SalonHeader name={booking.businessName} branchLine={branchLabel(booking.branch)} />
      <main className="mx-auto max-w-[34rem] px-5 pb-16">
        <div className="mb-6 rounded-2xl bg-(--brand) px-5 py-6 text-(--on-brand)">
          <h1 className="text-[1.75rem] leading-tight font-semibold text-balance">You're booked in</h1>
          <p className="mt-1 text-lg tabular-nums">
            {fullDate(booking.startAt, tz)} at {clock(booking.startAt, tz)}
          </p>
        </div>

        <BookingDetails booking={booking} />

        <section aria-labelledby="calendar" className="mt-6">
          <h2 id="calendar" className="mb-2 font-semibold">
            Add to your calendar
          </h2>
          <div className="flex flex-wrap gap-2">
            <a
              href={icsDataUrl(event, `${booking.reference}@${booking.businessSlug}`)}
              download={`${booking.businessSlug}-${booking.reference}.ics`}
              className="inline-flex h-11 items-center gap-2 rounded-full border border-[#CFCBC6] px-4 font-medium hover:border-[#1C1B1A]"
            >
              <CalendarPlus className="size-4" aria-hidden />
              Apple or Outlook
            </a>
            <a
              href={googleCalendarUrl(event)}
              target="_blank"
              rel="noreferrer"
              className="inline-flex h-11 items-center gap-2 rounded-full border border-[#CFCBC6] px-4 font-medium hover:border-[#1C1B1A]"
            >
              <CalendarPlus className="size-4" aria-hidden />
              Google Calendar
            </a>
          </div>
        </section>

        <section aria-labelledby="manage" className="mt-8 rounded-2xl bg-[#F3F2F0] p-5">
          <h2 id="manage" className="font-semibold">
            Need to change plans?
          </h2>
          <p className="mt-1 text-pretty text-[#6E6A65]">
            {booking.email ? `We've emailed the details and a private link to ${booking.email}. ` : ''}
            The link lets you check or cancel this booking; anyone with it can do the same.
          </p>
          <Link to={`/m/${manageToken}`} className="mt-3 inline-flex font-semibold underline underline-offset-4">
            Open my booking
          </Link>
        </section>

        <button type="button" onClick={onBookAnother} className="mt-8 font-medium underline underline-offset-4">
          Book another visit
        </button>
      </main>
    </>
  )
}
