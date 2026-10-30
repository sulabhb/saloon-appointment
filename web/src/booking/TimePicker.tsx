import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { addDays } from '../pages/schedule/schedule'
import type { Days, Slots } from './api'
import { Notice } from './BookingFrame'
import { clock } from './format'

const DAYS_SHOWN = 14
const weekdayShort = (date: string) =>
  new Intl.DateTimeFormat('en-AU', { weekday: 'short', timeZone: 'UTC' }).format(new Date(`${date}T00:00:00Z`))
const monthLong = (date: string) =>
  new Intl.DateTimeFormat('en-AU', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${date}T00:00:00Z`))

/** Today's date in the branch's time zone, as "YYYY-MM-DD". */
export const todayIn = (timeZone: string) => new Intl.DateTimeFormat('en-CA', { timeZone }).format(new Date())

/**
 * Two weeks of days (closed and full ones crossed out) and the free times of the chosen day,
 * grouped by part of the day (BKG-6, AVL-6). The caller supplies the queries, so the same picker
 * serves new bookings and moving an existing one.
 */
export function TimePicker({
  timeZone,
  date,
  start,
  onDate,
  onStart,
  withStylist,
  cacheKey,
  fetchDays,
  fetchSlots,
}: {
  timeZone: string
  date: string | null
  start: string | null
  onDate: (date: string) => void
  onStart: (start: string) => void
  /** A specific stylist is chosen, for the "nothing free" message. */
  withStylist: boolean
  /** Identifies what is being searched (services, stylist, booking); part of the cache key. */
  cacheKey: unknown[]
  fetchDays: (from: string, to: string) => Promise<Days>
  fetchSlots: (date: string) => Promise<Slots>
}) {
  const today = todayIn(timeZone)
  const [from, setFrom] = useState(() => (date && date >= today ? date : today))
  const to = addDays(from, DAYS_SHOWN - 1)
  const days = useQuery({
    queryKey: [...cacheKey, 'days', from, to],
    queryFn: () => fetchDays(from, to),
    placeholderData: keepPreviousData,
  })
  const firstAvailable = days.data?.days.find((d) => d.status === 'AVAILABLE')?.date ?? null
  const shown = date ?? firstAvailable
  const slots = useQuery({
    queryKey: [...cacheKey, 'slots', shown],
    queryFn: () => fetchSlots(shown!),
    enabled: Boolean(shown),
  })
  const atHorizon = days.data ? to >= days.data.lastBookableDate : false

  const groups = useMemo(() => {
    const list = slots.data?.slots ?? []
    return [
      { label: 'Morning', items: list.filter((s) => s.time < '12:00') },
      { label: 'Afternoon', items: list.filter((s) => s.time >= '12:00' && s.time < '17:00') },
      { label: 'Evening', items: list.filter((s) => s.time >= '17:00') },
    ].filter((g) => g.items.length > 0)
  }, [slots.data])

  return (
    <>
      {days.error && <Notice tone="error">{days.error.message}</Notice>}
      <div className="mb-2 flex items-center justify-between">
        <p className="font-semibold">{monthLong(shown ?? from)}</p>
        <div className="flex gap-1">
          <button
            type="button"
            disabled={from <= today}
            onClick={() => setFrom(addDays(from, -DAYS_SHOWN) < today ? today : addDays(from, -DAYS_SHOWN))}
            className="rounded-full px-3 py-1.5 text-sm font-medium hover:bg-[#F3F2F0] disabled:opacity-40"
          >
            Earlier
          </button>
          <button
            type="button"
            disabled={atHorizon}
            onClick={() => setFrom(addDays(from, DAYS_SHOWN))}
            className="rounded-full px-3 py-1.5 text-sm font-medium hover:bg-[#F3F2F0] disabled:opacity-40"
          >
            Later
          </button>
        </div>
      </div>
      <div role="radiogroup" aria-label="Date" className="-mx-5 flex gap-2 overflow-x-auto px-5 pb-2 [scrollbar-width:none]">
        {(days.data?.days ?? []).map((d) => {
          const open = d.status === 'AVAILABLE'
          const selected = d.date === shown
          return (
            <button
              key={d.date}
              type="button"
              role="radio"
              aria-checked={selected}
              disabled={!open}
              aria-label={`${weekdayShort(d.date)} ${Number(d.date.slice(8))}${open ? '' : d.status === 'FULL' ? ', fully booked' : ', closed'}`}
              onClick={() => onDate(d.date)}
              className={`flex h-[4.5rem] w-14 shrink-0 flex-col items-center justify-center rounded-2xl border transition-colors duration-150 ease-out ${
                selected
                  ? 'border-(--brand) bg-(--brand) text-(--on-brand)'
                  : open
                    ? 'border-[#E6E4E1] hover:border-[#1C1B1A]'
                    : 'border-transparent text-[#B4AFA9]'
              }`}
            >
              <span className="text-xs">{weekdayShort(d.date)}</span>
              <span className={`text-lg font-semibold tabular-nums ${open ? '' : 'line-through decoration-1'}`}>{Number(d.date.slice(8))}</span>
            </button>
          )
        })}
      </div>

      <div className="mt-5">
        {days.data && !firstAvailable && !date && (
          <Notice>
            Nothing free in these two weeks{withStylist ? ' with this stylist' : ''}.{' '}
            {!atHorizon && (
              <button type="button" onClick={() => setFrom(addDays(from, DAYS_SHOWN))} className="font-medium underline underline-offset-4">
                Look later
              </button>
            )}
          </Notice>
        )}
        {shown && slots.isPending && (
          <div role="status" aria-label="Loading" className="flex flex-col gap-3">
            {[0, 1].map((i) => (
              <div key={i} className="h-12 animate-pulse rounded-xl bg-[#F3F2F0]" />
            ))}
          </div>
        )}
        {slots.error && <Notice tone="error">{slots.error.message}</Notice>}
        {slots.data && slots.data.slots.length === 0 && <Notice>No times left on this day. Choose another day.</Notice>}
        {groups.map((g) => (
          <section key={g.label} className="mb-5" aria-label={g.label}>
            <h2 className="mb-2 text-sm text-[#6E6A65]">{g.label}</h2>
            <div role="radiogroup" aria-label={`${g.label} times`} className="grid grid-cols-3 gap-2 sm:grid-cols-4">
              {g.items.map((s) => {
                const selected = start === s.start
                return (
                  <button
                    key={s.start}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    onClick={() => {
                      if (!date && shown) onDate(shown)
                      onStart(s.start)
                    }}
                    className={`h-12 rounded-xl border text-[15px] font-semibold tabular-nums transition-colors duration-150 ease-out active:scale-[0.97] ${
                      selected ? 'border-(--brand) bg-(--brand) text-(--on-brand)' : 'border-[#E6E4E1] hover:border-[#1C1B1A]'
                    }`}
                  >
                    {clock(s.start, timeZone).replace(' ', ' ')}
                  </button>
                )
              })}
            </div>
          </section>
        ))}
      </div>
    </>
  )
}
