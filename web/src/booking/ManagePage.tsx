import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router'
import { ApiError } from '../api/client'
import {
  fetchRescheduleDays,
  fetchRescheduleSlots,
  useCancelBooking,
  useManagedBooking,
  useRescheduleBooking,
  useRescheduleOptions,
  type BookingView,
} from './api'
import { BookAgainLink } from './BookingPage'
import { BookingDetails } from './BookingDetails'
import { ActionBar, BookingFrame, Notice, SalonHeader } from './BookingFrame'
import { branchLabel, clock, fullDate, inZone, shortDate } from './format'
import { TimePicker } from './TimePicker'

const outlineButton = 'h-11 rounded-full border border-[#CFCBC6] px-5 font-medium hover:border-[#1C1B1A]'

/** The customer's private management link (MGT-1..5): check, move or cancel a booking. */
export function ManagePage() {
  const { token = '' } = useParams()
  const booking = useManagedBooking(token)
  const client = useQueryClient()
  const [mode, setMode] = useState<'view' | 'move' | 'cancel'>('view')
  const [moved, setMoved] = useState(false)
  const cancel = useCancelBooking(token)
  const view = booking.data

  if (booking.isPending) {
    return (
      <BookingFrame accentColor={null}>
        <div className="mx-auto max-w-[34rem] px-5 pt-24" role="status" aria-label="Loading">
          <div className="h-10 w-2/3 animate-pulse rounded-lg bg-[#F3F2F0]" />
        </div>
      </BookingFrame>
    )
  }
  if (!view) {
    return (
      <BookingFrame accentColor={null}>
        <main className="mx-auto max-w-[34rem] px-5 pt-24">
          <h1 className="text-2xl font-semibold">This link doesn't work</h1>
          <p className="mt-2 text-[#6E6A65]">
            {booking.error?.message ?? 'It may have expired.'} If you booked recently, use the newest email from the salon, or ask
            them for help.
          </p>
        </main>
      </BookingFrame>
    )
  }

  const tz = view.branch.timeZone
  const cancelled = view.status === 'CANCELLED'
  const update = (next: BookingView) => client.setQueryData(['manage', token], next)
  const deadline = `${inZone(view.cancelBefore, tz, { weekday: 'long', day: 'numeric', month: 'long' }).replace(',', '')} at ${clock(view.cancelBefore, tz)}`

  return (
    <BookingFrame accentColor={view.accentColor}>
      <SalonHeader name={view.businessName} branchLine={branchLabel(view.branch)} />
      <main className="mx-auto max-w-[34rem] px-5 pb-16">
        {mode === 'move' ? (
          <MoveBooking
            token={token}
            view={view}
            onBack={() => setMode('view')}
            onMoved={(next) => {
              update(next)
              setMoved(true)
              setMode('view')
              window.scrollTo({ top: 0 })
            }}
          />
        ) : (
          <>
            <h1 className="mb-1 text-[1.375rem] font-semibold">{cancelled ? 'This booking is cancelled' : 'Your booking'}</h1>
            <p className="mb-5 text-[#6E6A65]">
              {cancelled
                ? 'The time has been released. You can book again whenever suits.'
                : `Booked for ${view.customerName}. Contact details on file: ${[view.email, view.phone].filter(Boolean).join(' and ')}.`}
            </p>
            {moved && !cancelled && (
              <div className="mb-5">
                <Notice>
                  Moved to {fullDate(view.startAt, tz)} at {clock(view.startAt, tz)}. We've emailed you the new details.
                </Notice>
              </div>
            )}
            <div className={cancelled ? 'opacity-60' : ''}>
              <BookingDetails booking={view} />
            </div>

            {!cancelled && (
              <section aria-labelledby="change-heading" className="mt-8">
                <h2 id="change-heading" className="font-semibold">
                  Need to change it?
                </h2>
                {view.canCancel ? (
                  <>
                    <p className="mt-1 text-[#6E6A65]">You can move or cancel online until {deadline}.</p>
                    {cancel.error && (
                      <div className="mt-3">
                        <Notice tone="error">{cancel.error.message}</Notice>
                      </div>
                    )}
                    {mode === 'cancel' ? (
                      <div role="group" aria-label="Confirm cancellation" className="mt-4 flex flex-wrap items-center gap-2">
                        <span className="mr-1">Cancel this booking?</span>
                        <button type="button" onClick={() => setMode('view')} className={outlineButton}>
                          Keep it
                        </button>
                        <button
                          type="button"
                          disabled={cancel.isPending}
                          onClick={() => cancel.mutate(undefined, { onSuccess: (next) => { update(next); setMode('view') } })}
                          className="h-11 rounded-full bg-[#B3261E] px-5 font-semibold text-white transition-transform duration-150 ease-out active:scale-[0.97] disabled:opacity-60"
                        >
                          {cancel.isPending ? 'Cancelling…' : 'Yes, cancel'}
                        </button>
                      </div>
                    ) : (
                      <div className="mt-4 flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => {
                            setMoved(false)
                            setMode('move')
                            window.scrollTo({ top: 0 })
                          }}
                          className="h-11 rounded-full bg-(--brand) px-5 font-semibold text-(--on-brand) transition-transform duration-150 ease-out active:scale-[0.97]"
                        >
                          Change time
                        </button>
                        <button type="button" onClick={() => setMode('cancel')} className={outlineButton}>
                          Cancel booking
                        </button>
                      </div>
                    )}
                  </>
                ) : (
                  <p className="mt-1 text-[#6E6A65]">
                    It's too late to change this online.{' '}
                    {view.branch.phone ? (
                      <>
                        Call {view.branch.name} on{' '}
                        <a href={`tel:${view.branch.phone.replace(/\s/g, '')}`} className="font-medium text-[#1C1B1A] underline underline-offset-4 tabular-nums">
                          {view.branch.phone}
                        </a>
                        .
                      </>
                    ) : (
                      'Please contact the salon.'
                    )}
                  </p>
                )}
              </section>
            )}
            {cancelled && (
              <p className="mt-8">
                <BookAgainLink business={view.businessSlug} />
              </p>
            )}
            <p className="sr-only" aria-live="polite">
              {cancel.isSuccess && `Cancelled your booking on ${fullDate(view.startAt, tz)}.`}
            </p>
          </>
        )}
      </main>
    </BookingFrame>
  )
}

/**
 * Same branch and services at a new time (MGT-4). Keeps the current stylist by default; the
 * original time stands if the new one is taken in the meantime.
 */
function MoveBooking({
  token,
  view,
  onBack,
  onMoved,
}: {
  token: string
  view: BookingView
  onBack: () => void
  onMoved: (next: BookingView) => void
}) {
  const options = useRescheduleOptions(token, true)
  const move = useRescheduleBooking(token)
  const tz = view.branch.timeZone
  const [staffId, setStaffId] = useState<string | null | undefined>(undefined)
  const [date, setDate] = useState<string | null>(null)
  const [start, setStart] = useState<string | null>(null)
  const [gone, setGone] = useState(false)

  // Until the customer chooses, stay with their stylist if they can still be booked online.
  const current = options.data?.currentStaffId
  const chosen = staffId === undefined ? (options.data?.stylists.some((s) => s.id === current) ? current! : null) : staffId
  const failed = move.error instanceof ApiError && move.error.code !== 'SLOT_UNAVAILABLE' ? move.error : null

  return (
    <>
      <h1 className="text-[1.375rem] font-semibold">Choose a new time</h1>
      <p className="mt-1 mb-5 text-[#6E6A65]">
        Now: {fullDate(view.startAt, tz)} at {clock(view.startAt, tz)} with {view.staffName}. Same services and branch.
      </p>
      {options.error && <Notice tone="error">{options.error.message}</Notice>}
      {options.data && (
        <>
          {options.data.stylists.length > 1 && (
            <div className="mb-5 flex flex-col gap-1.5">
              <label htmlFor="stylist" className="font-semibold">
                Stylist
              </label>
              <select
                id="stylist"
                value={chosen ?? ''}
                onChange={(e) => {
                  setStaffId(e.target.value || null)
                  setDate(null)
                  setStart(null)
                }}
                className="h-12 w-full rounded-xl border border-[#CFCBC6] bg-white px-4 text-base"
              >
                <option value="">Anyone available</option>
                {options.data.stylists.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.displayName}
                    {s.id === current ? ' (your stylist)' : ''}
                  </option>
                ))}
              </select>
            </div>
          )}
          {gone && (
            <div className="mb-4">
              <Notice tone="error">That time was just booked by someone else. Your booking hasn't changed; pick another time.</Notice>
            </div>
          )}
          <TimePicker
            key={chosen ?? 'any'}
            timeZone={tz}
            date={date}
            start={start}
            onDate={(d) => {
              setDate(d)
              setStart(null)
            }}
            onStart={(s) => {
              setGone(false)
              setStart(s)
            }}
            withStylist={chosen !== null}
            cacheKey={['manage', token, 'move', chosen]}
            fetchDays={(from, to) => fetchRescheduleDays(token, chosen, from, to)}
            fetchSlots={(d) => fetchRescheduleSlots(token, chosen, d)}
          />
          {failed && <Notice tone="error">{failed.message}</Notice>}
        </>
      )}
      <button type="button" onClick={onBack} className="mt-6 font-medium underline underline-offset-4">
        Keep my booking as it is
      </button>
      {start ? (
        <ActionBar
          summary={`${shortDate(start, tz)}, ${clock(start, tz)}`}
          detail={chosen ? `With ${options.data?.stylists.find((s) => s.id === chosen)?.displayName}` : 'With whoever is free'}
          action="Move booking"
          busy={move.isPending}
          onAction={() =>
            move.mutate(
              { start, staffId: chosen },
              {
                onSuccess: onMoved,
                onError: (error) => {
                  if (error instanceof ApiError && error.code === 'SLOT_UNAVAILABLE') {
                    setGone(true)
                    setStart(null)
                  }
                },
              },
            )
          }
        />
      ) : (
        <ActionBar summary="Choose a new time" action="Move booking" onAction={() => undefined} disabled />
      )}
    </>
  )
}
