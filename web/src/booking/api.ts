// Public booking API (/api/v1/public). No sign-in; the business comes from the URL.
import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

export interface PublicBranch {
  slug: string
  name: string
  suburb: string
  state: string
  phone: string | null
  timeZone: string
}

export interface PublicBusiness {
  slug: string
  displayName: string
  description: string | null
  accentColor: string | null
  /** Customers can cancel online until this many minutes before the start. */
  cancelCutoffMin: number
  locations: PublicBranch[]
}

export interface ServiceOption {
  id: string
  name: string
  description: string | null
  durationMin: number
  priceCents: number
  priceIsFrom: boolean
  customerNotice: string | null
}

export interface BranchCatalog {
  categories: { name: string; services: ServiceOption[] }[]
  stylists: { id: string; displayName: string; bio: string | null; serviceIds: string[] }[]
}

export interface Slots {
  date: string
  timeZone: string
  durationMin: number
  totalPriceCents: number
  slots: { start: string; time: string }[]
}

export type DayStatus = 'AVAILABLE' | 'FULL' | 'CLOSED'

export interface Days {
  days: { date: string; status: DayStatus }[]
  lastBookableDate: string
}

export interface BookingView {
  reference: string
  status: 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'
  businessName: string
  businessSlug: string
  accentColor: string | null
  branch: {
    name: string
    addressLine1: string
    addressLine2: string | null
    suburb: string
    state: string
    postcode: string
    phone: string | null
    timeZone: string
  }
  startAt: string
  endAt: string
  services: { name: string; durationMin: number; priceCents: number; priceIsFrom: boolean }[]
  totalPriceCents: number
  staffName: string
  customerName: string
  email: string | null
  phone: string | null
  canCancel: boolean
  cancelBefore: string
}

export interface NewBooking {
  locationSlug: string
  serviceIds: string[]
  staffId: string | null
  start: string
  customer: { fullName: string; email: string; phone: string }
  note?: string
  acceptsPolicy: boolean
  idempotencyKey: string
}

const base = (business: string) => `/public/b/${encodeURIComponent(business)}`
const servicesParam = (ids: string[], staffId: string | null) =>
  `services=${ids.join(',')}${staffId ? `&staff=${staffId}` : ''}`

export const usePublicBusiness = (business: string) =>
  useQuery({ queryKey: ['public', business], queryFn: () => api<PublicBusiness>(base(business)), retry: false })

export const useBranchCatalog = (business: string, branch: string | undefined) =>
  useQuery({
    queryKey: ['public', business, branch, 'catalog'],
    queryFn: () => api<BranchCatalog>(`${base(business)}/locations/${branch}/catalog`),
    enabled: Boolean(branch),
  })

export const fetchDays = (business: string, branch: string, serviceIds: string[], staffId: string | null, from: string, to: string) =>
  api<Days>(`${base(business)}/locations/${branch}/availability/days?${servicesParam(serviceIds, staffId)}&from=${from}&to=${to}`)

export const fetchSlots = (business: string, branch: string, serviceIds: string[], staffId: string | null, date: string) =>
  api<Slots>(`${base(business)}/locations/${branch}/availability?${servicesParam(serviceIds, staffId)}&date=${date}`)

export const useCreateBooking = (business: string) =>
  useMutation({
    mutationFn: (booking: NewBooking) =>
      api<{ booking: BookingView; manageToken: string }>(`${base(business)}/bookings`, { method: 'POST', body: booking }),
  })

export const useManagedBooking = (token: string) =>
  useQuery({ queryKey: ['manage', token], queryFn: () => api<BookingView>(`/public/manage/${token}`), retry: false })

export const useCancelBooking = (token: string) =>
  useMutation({ mutationFn: () => api<BookingView>(`/public/manage/${token}/cancel`, { method: 'POST' }) })

/* Moving a booking through its management link (MGT-4). */

export interface RescheduleOptions {
  currentStaffId: string
  stylists: { id: string; displayName: string }[]
}

const staffParam = (staffId: string | null) => (staffId ? `&staff=${staffId}` : '')

export const useRescheduleOptions = (token: string, enabled: boolean) =>
  useQuery({
    queryKey: ['manage', token, 'options'],
    queryFn: () => api<RescheduleOptions>(`/public/manage/${token}/options`),
    enabled,
  })

export const fetchRescheduleDays = (token: string, staffId: string | null, from: string, to: string) =>
  api<Days>(`/public/manage/${token}/availability/days?from=${from}&to=${to}${staffParam(staffId)}`)

export const fetchRescheduleSlots = (token: string, staffId: string | null, date: string) =>
  api<Slots>(`/public/manage/${token}/availability?date=${date}${staffParam(staffId)}`)

export const useRescheduleBooking = (token: string) =>
  useMutation({
    mutationFn: (input: { start: string; staffId: string | null }) =>
      api<BookingView>(`/public/manage/${token}/reschedule`, { method: 'POST', body: input }),
  })
