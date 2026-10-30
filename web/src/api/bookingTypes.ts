// Staff-side booking API (/api/v1/staff). Times: instants as ISO strings; local times "HH:mm:ss".
import type { AppointmentStatus } from './types'

export interface BookingOptions {
  categories: {
    name: string
    services: { id: string; name: string; description: string | null; durationMin: number; priceCents: number; priceIsFrom: boolean }[]
  }[]
  stylists: { id: string; displayName: string; bio: string | null; serviceIds: string[] }[]
}

export interface StaffSlots {
  date: string
  timeZone: string
  durationMin: number
  totalPriceCents: number
  slots: { start: string; time: string; staffIds: string[] }[]
}

export interface AppointmentDetail {
  id: string
  reference: string
  status: AppointmentStatus
  startAt: string
  endAt: string
  locationId: string
  locationName: string
  timeZone: string
  staffId: string
  staffName: string
  customer: { id: string; fullName: string; email: string | null; phone: string | null }
  services: { serviceId: string; name: string; durationMin: number; priceCents: number; priceIsFrom: boolean }[]
  totalPriceCents: number
  customerNote: string | null
  internalNote: string | null
  source: 'web' | 'android' | 'staff' | 'walk_in'
  createdAt: string
  createdByName: string | null
  statusChangedAt: string | null
  cancelledBy: 'customer' | 'staff' | null
  cancelReason: string | null
  lateCancel: boolean
  allowedTransitions: AppointmentStatus[]
  canReschedule: boolean
  canEditNote: boolean
  emails: EmailRecord[]
}

/** "sent" means the mail service accepted it, not that it was delivered (NTF-6). */
export interface EmailRecord {
  type: 'booking_confirmed' | 'booking_rescheduled' | 'booking_cancelled' | 'reminder' | 'manage_link'
  status: 'queued' | 'sending' | 'sent' | 'failed' | 'skipped'
  recipient: string
  attempts: number
  lastError: string | null
  sentAt: string | null
  createdAt: string
}

export interface NewStaffBooking {
  locationId: string
  serviceIds: string[]
  staffId: string | null
  start: string
  customerId?: string
  customer?: { fullName: string; email?: string; phone?: string }
  note?: string
  internalNote?: string
  walkIn: boolean
  idempotencyKey: string
}

export interface CalendarAppointment {
  id: string
  reference: string | null
  status: AppointmentStatus
  startAt: string
  endAt: string
  blockedUntil: string
  customerName: string | null
  services: string | null
  hasNote: boolean
  /** At another branch: blocks the stylist, shown without customer details. */
  elsewhere: boolean
  locationName: string
}

/** [start, end] as "HH:mm:ss". */
export type LocalRange = [string, string]

export interface CalendarColumn {
  staffId: string
  displayName: string
  bookableOnline: boolean
  work: LocalRange[]
  timeOff: { kind: 'LEAVE' | 'BREAK'; from: string | null; to: string | null; reason: string | null }[]
  appointments: CalendarAppointment[]
}

export interface DayCalendar {
  date: string
  locationId: string
  timeZone: string
  opening: LocalRange[]
  closureReason: string | null
  closed: boolean
  columns: CalendarColumn[]
}

export interface CustomerMatch {
  id: string
  fullName: string
  email: string | null
  phone: string | null
  lastVisit: string | null
  upcoming: number
}
