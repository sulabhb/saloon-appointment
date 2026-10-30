// Response shapes of the backend API. Hand-written until the OpenAPI client is generated.

export type StaffRole = 'STYLIST' | 'RECEPTION' | 'MANAGER' | 'OWNER'
export type BusinessStatus = 'ONBOARDING' | 'LIVE' | 'SUSPENDED' | 'CLOSED'
export type InviteStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED'

export interface UserView {
  id: string
  email: string
  fullName: string
  platformAdmin: boolean
}

export interface BusinessMembership {
  businessId: string
  slug: string
  displayName: string
  role: StaffRole
}

export interface SelectedBusiness {
  businessId: string
  role: StaffRole
  /** null when the person sees every branch. */
  locationIds: string[] | null
  /** Their own staff profile here, if they perform services. */
  staffId: string | null
}

export interface Me {
  user: UserView
  businesses: BusinessMembership[]
  selected: SelectedBusiness | null
}

/** token is only returned to Android; the web gets a cookie. */
export interface SignInResponse {
  token: string | null
  me: Me
}

export interface InvitePreview {
  businessName: string
  email: string
  role: StaffRole
  accountExists: boolean
  expiresAt: string
}

export interface BusinessSummary {
  id: string
  slug: string
  displayName: string
  status: BusinessStatus
  createdAt: string
  activeBranches: number
  activeStaff: number
  pendingInvites: number
}

export interface IssuedInvite {
  id: string
  email: string
  role: StaffRole
  /** The only copy of the invite link; it cannot be shown again. */
  url: string
  expiresAt: string
}

export interface InviteView {
  id: string
  email: string
  role: StaffRole
  status: InviteStatus
  expiresAt: string
  createdAt: string
}

export interface CreatedBusiness {
  business: BusinessSummary
  ownerInvite: IssuedInvite
}

export interface BusinessDetail {
  business: BusinessSummary
  invites: InviteView[]
}

export interface StaffLocation {
  id: string
  slug: string
  name: string
  suburb: string
  state: string
  timeZone: string
}

export type AppointmentStatus = 'CONFIRMED' | 'CHECKED_IN' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'

/** One appointment on the staff agenda. Times are instants; show them in the branch's time zone. */
export interface AgendaItem {
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
  customerName: string
  customerPhone: string | null
  customerEmail: string | null
  services: string
  customerNote: string | null
  source: string
}
