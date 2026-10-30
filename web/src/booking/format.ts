// Formatting for the booking pages. Times are always shown in the branch's own time zone.

export const FALLBACK_BRAND = '#2F5D50'

export function inZone(instant: string, timeZone: string, options: Intl.DateTimeFormatOptions) {
  return new Intl.DateTimeFormat('en-AU', { ...options, timeZone }).format(new Date(instant))
}

/** "9:30 am". */
export const clock = (instant: string, timeZone: string) => inZone(instant, timeZone, { hour: 'numeric', minute: '2-digit' })

/** "Monday 3 November". */
export const fullDate = (instant: string, timeZone: string) =>
  inZone(instant, timeZone, { weekday: 'long', day: 'numeric', month: 'long' }).replace(',', '')

/** "Sat 31 Oct": short enough for the action bar on a phone. */
export const shortDate = (instant: string, timeZone: string) =>
  inZone(instant, timeZone, { weekday: 'short', day: 'numeric', month: 'short' }).replace(',', '')

/** "24 hours", "2 days", "90 minutes": the cancellation cut-off in words. */
export function cutoffInWords(minutes: number) {
  if (minutes % 1440 === 0 && minutes >= 2880) return `${minutes / 1440} days`
  if (minutes % 60 === 0) return `${minutes / 60} ${minutes === 60 ? 'hour' : 'hours'}`
  return `${minutes} minutes`
}

/** "2026-11-03T09:00:00Z" → "20261103T090000Z" for calendar files and links. */
const calendarStamp = (instant: string) => new Date(instant).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')

export interface CalendarEvent {
  title: string
  start: string
  end: string
  location: string
  details: string
}

export function googleCalendarUrl(e: CalendarEvent) {
  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: e.title,
    dates: `${calendarStamp(e.start)}/${calendarStamp(e.end)}`,
    location: e.location,
    details: e.details,
  })
  return `https://calendar.google.com/calendar/render?${params}`
}

/** An .ics file as a data URL, for Apple Calendar and Outlook (BKG-11). */
export function icsDataUrl(e: CalendarEvent, uid: string) {
  const escape = (text: string) => text.replace(/[\\,;]/g, (c) => `\\${c}`).replace(/\n/g, '\\n')
  const ics = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Salon Booking//EN',
    'BEGIN:VEVENT',
    `UID:${uid}`,
    `DTSTAMP:${calendarStamp(new Date().toISOString())}`,
    `DTSTART:${calendarStamp(e.start)}`,
    `DTEND:${calendarStamp(e.end)}`,
    `SUMMARY:${escape(e.title)}`,
    `LOCATION:${escape(e.location)}`,
    `DESCRIPTION:${escape(e.details)}`,
    'END:VEVENT',
    'END:VCALENDAR',
  ].join('\r\n')
  return `data:text/calendar;charset=utf-8,${encodeURIComponent(ics)}`
}

/** "Fortitude Valley, QLD" when the branch is named after its suburb, otherwise "Bondi Junction, Bondi". */
export const branchLabel = (b: { name: string; suburb: string; state: string }) =>
  `${b.name}, ${b.name.trim().toLowerCase() === b.suburb.trim().toLowerCase() ? b.state : b.suburb}`
