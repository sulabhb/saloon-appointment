import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api } from './client'
import type {
  AgendaItem,
  BusinessDetail,
  BusinessStatus,
  BusinessSummary,
  CreatedBusiness,
  InvitePreview,
  IssuedInvite,
  Me,
  SignInResponse,
  StaffLocation,
} from './types'

export const keys = {
  me: ['me'] as const,
  businesses: ['platform', 'businesses'] as const,
  business: (id: string) => ['platform', 'businesses', id] as const,
  invite: (token: string) => ['invite', token] as const,
  locations: ['staff', 'locations'] as const,
  agenda: (date: string) => ['staff', 'agenda', date] as const,
}

/** The signed-in person, or null when signed out. */
export function useMe() {
  return useQuery({
    queryKey: keys.me,
    queryFn: async () => {
      try {
        return await api<Me>('/auth/me')
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }
    },
    staleTime: 60_000,
  })
}

export function useSignIn() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: { email: string; password: string }) =>
      api<SignInResponse>('/auth/login', {
        method: 'POST',
        body: { ...input, client: 'web', deviceName: navigator.userAgent.slice(0, 100) },
      }),
    onSuccess: (response) => {
      // Everything cached belonged to whoever was signed in before.
      client.clear()
      client.setQueryData(keys.me, response.me)
    },
  })
}

export function useSignOut() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/auth/logout', { method: 'POST' }),
    onSettled: () => {
      client.clear()
      client.setQueryData(keys.me, null)
    },
  })
}

export function useSelectBusiness() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (businessId: string) => api<Me>('/auth/business', { method: 'POST', body: { businessId } }),
    onSuccess: (me) => {
      // Business data from the previous business must not show under the new one.
      client.removeQueries({ queryKey: ['staff'] })
      client.setQueryData(keys.me, me)
    },
  })
}

export function useInvite(token: string) {
  return useQuery({
    queryKey: keys.invite(token),
    queryFn: () => api<InvitePreview>(`/invites/${encodeURIComponent(token)}`),
    retry: false,
  })
}

export function useAcceptInvite(token: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: { fullName?: string; password: string }) =>
      api<SignInResponse>(`/invites/${encodeURIComponent(token)}/accept`, {
        method: 'POST',
        body: { ...input, client: 'web', deviceName: navigator.userAgent.slice(0, 100) },
      }),
    onSuccess: (response) => {
      client.clear()
      client.setQueryData(keys.me, response.me)
    },
  })
}

export function useBusinesses() {
  return useQuery({ queryKey: keys.businesses, queryFn: () => api<BusinessSummary[]>('/platform/businesses') })
}

export function useBusiness(id: string) {
  return useQuery({ queryKey: keys.business(id), queryFn: () => api<BusinessDetail>(`/platform/businesses/${id}`) })
}

export interface NewBusinessInput {
  slug: string
  legalName: string
  displayName: string
  defaultTimeZone: string
  ownerEmail: string
  accentColor?: string
  description?: string
}

export function useCreateBusiness() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: NewBusinessInput) => api<CreatedBusiness>('/platform/businesses', { method: 'POST', body: input }),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.businesses }),
  })
}

export function useChangeStatus(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (status: BusinessStatus) =>
      api<BusinessSummary>(`/platform/businesses/${id}/status`, { method: 'POST', body: { status } }),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.businesses }),
  })
}

export function useInviteOwner(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (email: string) =>
      api<IssuedInvite>(`/platform/businesses/${id}/owner-invites`, { method: 'POST', body: { email } }),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.businesses }),
  })
}

export function useRevokeInvite(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (inviteId: string) => api<void>(`/platform/businesses/${id}/invites/${inviteId}`, { method: 'DELETE' }),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.businesses }),
  })
}

export function useStaffLocations(enabled: boolean) {
  return useQuery({
    queryKey: keys.locations,
    queryFn: () => api<StaffLocation[]>('/staff/locations'),
    enabled,
  })
}

/** The day's appointments at the branches this person can access; refreshed every minute. */
export function useAgenda(date: string, enabled: boolean) {
  return useQuery({
    queryKey: keys.agenda(date),
    queryFn: () => api<AgendaItem[]>(`/staff/agenda?date=${date}`),
    enabled,
    refetchInterval: 60_000,
  })
}
