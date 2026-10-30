// Times for the staff calendar, always in the branch's time zone.

/** "09:30:00" → 570. */
export const minutesOf = (time: string) => {
  const [h, m] = time.split(':').map(Number) as [number, number]
  return h * 60 + m
}

/** 570 → "09:30". */
export const hhmm = (minutes: number) =>
  `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`

/** Minutes after midnight of an instant, in the branch's time zone. */
export function localMinutes(instant: string | Date, timeZone: string) {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .formatToParts(typeof instant === 'string' ? new Date(instant) : instant)
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? 0)
  return get('hour') * 60 + get('minute')
}

/** "9:30 am". */
export const branchClock = (instant: string, timeZone: string) =>
  new Intl.DateTimeFormat('en-AU', { hour: 'numeric', minute: '2-digit', timeZone }).format(new Date(instant))

/** "Monday 3 November". */
export const branchDate = (instant: string, timeZone: string) =>
  new Intl.DateTimeFormat('en-AU', { weekday: 'long', day: 'numeric', month: 'long', timeZone })
    .format(new Date(instant))
    .replace(',', '')

/** Today in the branch's time zone, "YYYY-MM-DD". */
export const todayIn = (timeZone: string) => new Intl.DateTimeFormat('en-CA', { timeZone }).format(new Date())

/** "9 am" for the time axis. */
export const hourLabel = (minutes: number) => {
  const h = Math.floor(minutes / 60)
  return `${h % 12 === 0 ? 12 : h % 12} ${h < 12 ? 'am' : 'pm'}`
}

/**
 * The instant at which the branch's clock shows {@code minutes} after midnight on {@code date}.
 * Starts from that wall time read as UTC and corrects by the zone's offset; a second pass settles
 * days when the offset changes (daylight saving).
 */
export function zonedInstant(date: string, minutes: number, timeZone: string): string {
  const [y, m, d] = date.split('-').map(Number) as [number, number, number]
  const wanted = Date.UTC(y, m - 1, d, Math.floor(minutes / 60), minutes % 60)
  let t = wanted
  for (let i = 0; i < 2; i++) {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(new Date(t))
    const get = (type: string) => Number(parts.find((p) => p.type === type)?.value)
    const shown = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'))
    t -= shown - wanted
  }
  return new Date(t).toISOString()
}
