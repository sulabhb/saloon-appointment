// Business setup API (/api/v1/admin). Hand-written until the OpenAPI client is generated.
import type { BusinessStatus, StaffRole } from './types'

export interface BusinessProfile {
  id: string
  slug: string
  displayName: string
  description: string | null
  accentColor: string | null
  contactEmail: string | null
  status: BusinessStatus
}

export interface SetupStatus {
  branchWithHours: boolean
  serviceOffered: boolean
  bookableStaff: boolean
  status: BusinessStatus
  canGoLive: boolean
}

/** Online booking rules (DSH-17). Staff bookings ignore notice, horizon and the per-customer limit. */
export interface BookingPolicy {
  slotIntervalMin: number
  minNoticeMin: number
  horizonDays: number
  cancelCutoffMin: number
  maxFutureBookingsPerContact: number
}

/** Branch overrides; null means "same as the business". */
export interface BranchPolicy {
  slotIntervalMin: number | null
  minNoticeMin: number | null
  horizonDays: number | null
}

export interface BusinessOverview {
  profile: BusinessProfile
  setup: SetupStatus
  policy: BookingPolicy
}

export interface BranchSummary {
  id: string
  name: string
  slug: string
  suburb: string
  state: string
  timeZone: string
  active: boolean
  openDays: number
  servicesOffered: number
  staffCount: number
}

export interface HoursInterval {
  weekday: number
  /** "HH:mm:ss" from the server; "HH:mm" accepted when sending. */
  opensAt: string
  closesAt: string
}

export interface Closure {
  id: string
  startsOn: string
  endsOn: string
  reason: string | null
}

export interface BranchService {
  serviceId: string
  name: string
  categoryName: string
  offered: boolean
  priceCents: number | null
  durationMin: number | null
  defaultPriceCents: number
  defaultDurationMin: number
}

export interface BranchDetail {
  id: string
  name: string
  slug: string
  addressLine1: string
  addressLine2: string | null
  suburb: string
  state: string
  postcode: string
  phone: string | null
  email: string | null
  timeZone: string
  active: boolean
  hours: HoursInterval[]
  closures: Closure[]
  services: BranchService[]
  policy: BranchPolicy
}

export interface BranchInput {
  name: string
  slug: string
  addressLine1: string
  addressLine2?: string
  suburb: string
  state: string
  postcode: string
  phone?: string
  email?: string
  timeZone: string
  active?: boolean
}

export interface ServiceView {
  id: string
  categoryId: string
  name: string
  description: string | null
  durationMin: number
  bufferAfterMin: number
  priceCents: number
  priceIsFrom: boolean
  customerNotice: string | null
  active: boolean
  branchCount: number
  staffCount: number
}

export interface CategoryView {
  id: string
  name: string
  sortOrder: number
  services: ServiceView[]
}

export interface ServiceInput {
  categoryId: string
  name: string
  description?: string
  durationMin: number
  bufferAfterMin: number
  priceCents: number
  priceIsFrom: boolean
  customerNotice?: string
  active?: boolean
  offerAtLocationIds?: string[]
}

export interface StaffView {
  id: string
  displayName: string
  bio: string | null
  bookableOnline: boolean
  active: boolean
  locationIds: string[]
  serviceIds: string[]
  login: { email: string; role: StaffRole; status: 'ACTIVE' | 'INVITED' } | null
}

export interface StaffInput {
  displayName: string
  bio?: string
  bookableOnline: boolean
  active?: boolean
  locationIds: string[]
  serviceIds: string[]
}

export interface MemberView {
  id: string
  userId: string
  fullName: string
  email: string
  role: StaffRole
  active: boolean
  staffId: string | null
  locationIds: string[]
}

export interface TeamInviteInput {
  email: string
  role: StaffRole
  staffId?: string
  locationIds: string[]
}

/* Schedules. Times are local to the branch: "HH:mm:ss" from the server, "HH:mm" accepted when sending. */

export interface WorkInterval {
  locationId: string
  startsAt: string
  endsAt: string
}

export interface WeeklyShift extends WorkInterval {
  /** ISO weekday: 1 = Monday. */
  weekday: number
}

export type TimeOffKind = 'LEAVE' | 'BREAK'

/** Leave covers whole days (no times); a break is part of one day. */
export interface TimeOff {
  id: string
  kind: TimeOffKind
  startsOn: string
  endsOn: string
  startsAt: string | null
  endsAt: string | null
  reason: string | null
}

export interface TimeOffInput {
  kind: TimeOffKind
  startsOn: string
  endsOn?: string
  startsAt?: string
  endsAt?: string
  reason?: string
}

/** A date that does not follow the usual week; no intervals means a day off. */
export interface DayOverride {
  date: string
  intervals: WorkInterval[]
}

export interface StaffSchedule {
  staffId: string
  displayName: string
  locationIds: string[]
  weekly: WeeklyShift[]
  overrides: DayOverride[]
  timeOff: TimeOff[]
}

export interface ScheduleWarning {
  code: 'OUTSIDE_HOURS' | 'BRANCH_CLOSED' | 'ON_LEAVE' | 'BOOKED_APPOINTMENT'
  message: string
}

export interface RotaDay {
  date: string
  override: boolean
  intervals: WorkInterval[]
  timeOff: TimeOff[]
}

export interface RotaRow {
  staffId: string
  displayName: string
  locationIds: string[]
  days: RotaDay[]
}

export interface Rota {
  weekStart: string
  staff: RotaRow[]
  closures: { locationId: string; startsOn: string; endsOn: string; reason: string | null }[]
}
