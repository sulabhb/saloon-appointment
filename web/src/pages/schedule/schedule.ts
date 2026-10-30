// Date and time helpers for schedules. Dates are ISO strings ("2030-06-03") handled in UTC so the
// browser's time zone never shifts a day; times are branch-local "HH:mm" or "HH:mm:ss".

export const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

const utc = (iso: string) => new Date(`${iso}T00:00:00Z`)
const iso = (date: Date) => date.toISOString().slice(0, 10)

/** Today in the browser's time zone. */
export function today(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

export function addDays(date: string, days: number): string {
  const d = utc(date)
  d.setUTCDate(d.getUTCDate() + days)
  return iso(d)
}

/** ISO weekday, 1 = Monday. */
export function weekdayOf(date: string): number {
  return ((utc(date).getUTCDay() + 6) % 7) + 1
}

export function mondayOf(date: string): string {
  return addDays(date, 1 - weekdayOf(date))
}

const dayMonth = new Intl.DateTimeFormat('en-AU', { day: 'numeric', month: 'long', timeZone: 'UTC' })
const dayMonthYear = new Intl.DateTimeFormat('en-AU', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
const longDay = new Intl.DateTimeFormat('en-AU', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' })

/** "3 – 9 June 2030", or "30 June – 6 July 2030" across months. */
export function weekLabel(monday: string): string {
  const sunday = addDays(monday, 6)
  const start = utc(monday).getUTCMonth() === utc(sunday).getUTCMonth() ? String(utc(monday).getUTCDate()) : dayMonth.format(utc(monday))
  return `${start} – ${dayMonthYear.format(utc(sunday))}`
}

/** "Monday 3 June". */
export function longDate(date: string): string {
  return longDay.format(utc(date)).replace(',', '')
}

/** "9am", "12:30pm": short enough for a rota cell. */
export function shortTime(time: string): string {
  const [h, m] = time.split(':').map(Number) as [number, number]
  const hour = h % 12 === 0 ? 12 : h % 12
  return `${hour}${m ? `:${String(m).padStart(2, '0')}` : ''}${h < 12 ? 'am' : 'pm'}`
}

export function timeRange(startsAt: string, endsAt: string): string {
  return `${shortTime(startsAt)}–${shortTime(endsAt)}`
}

type Interval = { locationId: string; startsAt: string; endsAt: string }

/** What is wrong with one day's work, if anything. Same rules as the server. */
export function intervalsError(intervals: Interval[]): string | undefined {
  const hm = (t: string) => t.slice(0, 5)
  if (intervals.some((iv) => hm(iv.startsAt) >= hm(iv.endsAt))) return 'Start time must be before end time.'
  const sorted = [...intervals].sort((a, b) => hm(a.startsAt).localeCompare(hm(b.startsAt)))
  const clash = sorted.findIndex((iv, i) => i > 0 && hm(iv.startsAt) < hm(sorted[i - 1]!.endsAt))
  if (clash < 0) return undefined
  return sorted[clash]!.locationId === sorted[clash - 1]!.locationId
    ? 'Shifts overlap.'
    : "Can't work at two branches at the same time."
}

/**
 * A second shift on a day: one running through lunch is split into a midday break,
 * otherwise an evening shift follows.
 */
export function addShift<T extends Interval>(intervals: T[], template: T): T[] {
  const last = intervals.at(-1)
  if (!last) return [{ ...template, startsAt: '09:00', endsAt: '17:00' }]
  const [start, end] = [last.startsAt.slice(0, 5), last.endsAt.slice(0, 5)]
  if (start < '12:30' && end > '13:30') {
    return [...intervals.slice(0, -1), { ...last, startsAt: start, endsAt: '12:30' }, { ...last, startsAt: '13:30', endsAt: end }]
  }
  const [h, m] = end.split(':').map(Number) as [number, number]
  const pad = (n: number) => String(n).padStart(2, '0')
  const from = Math.min(h + 1, 21)
  return [...intervals, { ...last, startsAt: `${pad(from)}:${pad(m)}`, endsAt: `${pad(Math.min(from + 2, 23))}:${pad(m)}` }]
}
