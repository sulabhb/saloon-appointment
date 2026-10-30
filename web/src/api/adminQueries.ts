import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  BookingPolicy,
  BranchDetail,
  BranchPolicy,
  BranchInput,
  BranchSummary,
  BusinessOverview,
  CategoryView,
  HoursInterval,
  MemberView,
  Rota,
  ScheduleWarning,
  ServiceInput,
  StaffSchedule,
  StaffInput,
  StaffView,
  TeamInviteInput,
  TimeOffInput,
  WeeklyShift,
  WorkInterval,
} from './adminTypes'
import { api } from './client'
import type { InviteView, IssuedInvite } from './types'

// Everything under 'staff' is dropped when the person switches business (useSelectBusiness).
export const adminKeys = {
  all: ['staff', 'admin'] as const,
  overview: ['staff', 'admin', 'overview'] as const,
  branches: ['staff', 'admin', 'branches'] as const,
  branch: (id: string) => ['staff', 'admin', 'branches', id] as const,
  catalog: ['staff', 'admin', 'catalog'] as const,
  staff: ['staff', 'admin', 'staff'] as const,
  members: ['staff', 'admin', 'members'] as const,
  invites: ['staff', 'admin', 'invites'] as const,
  rotas: ['staff', 'admin', 'rota'] as const,
  rota: (date: string, locationId?: string) => ['staff', 'admin', 'rota', date, locationId ?? 'all'] as const,
  schedule: (staffId: string) => ['staff', 'admin', 'schedule', staffId] as const,
}

/* Business */

export const useOverview = () =>
  useQuery({ queryKey: adminKeys.overview, queryFn: () => api<BusinessOverview>('/admin/business') })

export function useUpdateProfile() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: { displayName: string; description?: string; accentColor?: string; contactEmail?: string }) =>
      api<BusinessOverview>('/admin/business', { method: 'PUT', body: input }),
    onSuccess: (data) => client.setQueryData(adminKeys.overview, data),
  })
}

export function useUpdatePolicy() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (policy: BookingPolicy) => api<BusinessOverview>('/admin/business/policy', { method: 'PUT', body: policy }),
    onSuccess: (data) => client.setQueryData(adminKeys.overview, data),
  })
}

export function useGoLive() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api<BusinessOverview>('/admin/business/go-live', { method: 'POST' }),
    onSuccess: (data) => client.setQueryData(adminKeys.overview, data),
  })
}

/* Branches */

export const useBranches = () =>
  useQuery({ queryKey: adminKeys.branches, queryFn: () => api<BranchSummary[]>('/admin/locations') })

export const useBranch = (id: string) =>
  useQuery({ queryKey: adminKeys.branch(id), queryFn: () => api<BranchDetail>(`/admin/locations/${id}`) })

/** Branch changes also change the setup checklist and the branch list counts. */
function useBranchMutation<TInput>(id: string, request: (input: TInput) => Promise<BranchDetail>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (detail) => {
      client.setQueryData(adminKeys.branch(id), detail)
      client.invalidateQueries({ queryKey: adminKeys.branches })
      client.invalidateQueries({ queryKey: adminKeys.overview })
    },
  })
}

export function useCreateBranch() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: BranchInput) => api<{ id: string }>('/admin/locations', { method: 'POST', body: input }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: adminKeys.branches })
      client.invalidateQueries({ queryKey: adminKeys.overview })
    },
  })
}

export const useUpdateBranch = (id: string) =>
  useBranchMutation(id, (input: BranchInput) => api<BranchDetail>(`/admin/locations/${id}`, { method: 'PUT', body: input }))

export const useSaveHours = (id: string) =>
  useBranchMutation(id, (hours: HoursInterval[]) =>
    api<BranchDetail>(`/admin/locations/${id}/hours`, { method: 'PUT', body: { hours } }),
  )

export const useSetBranchPolicy = (id: string) =>
  useBranchMutation(id, (policy: BranchPolicy) => api<BranchDetail>(`/admin/locations/${id}/policy`, { method: 'PUT', body: policy }))

export const useAddClosure = (id: string) =>
  useBranchMutation(id, (input: { startsOn: string; endsOn: string; reason?: string }) =>
    api<BranchDetail>(`/admin/locations/${id}/closures`, { method: 'POST', body: input }),
  )

export function useRemoveClosure(id: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (closureId: string) => api<void>(`/admin/locations/${id}/closures/${closureId}`, { method: 'DELETE' }),
    onSuccess: () => client.invalidateQueries({ queryKey: adminKeys.branch(id) }),
  })
}

export const useSetOffering = (id: string) =>
  useBranchMutation(id, (input: { serviceId: string; offered: boolean; priceCents?: number | null; durationMin?: number | null }) =>
    api<BranchDetail>(`/admin/locations/${id}/services/${input.serviceId}`, {
      method: 'PUT',
      body: { offered: input.offered, priceCents: input.priceCents ?? null, durationMin: input.durationMin ?? null },
    }),
  )

/* Services */

export const useCatalog = () =>
  useQuery({ queryKey: adminKeys.catalog, queryFn: () => api<CategoryView[]>('/admin/catalog') })

function useCatalogMutation<TInput>(request: (input: TInput) => Promise<CategoryView[]>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (catalog) => {
      client.setQueryData(adminKeys.catalog, catalog)
      // Branch pages list services and the checklist depends on them.
      client.invalidateQueries({ queryKey: adminKeys.branches })
      client.invalidateQueries({ queryKey: adminKeys.overview })
    },
  })
}

export const useCreateCategory = () =>
  useCatalogMutation((name: string) => api<CategoryView[]>('/admin/categories', { method: 'POST', body: { name } }))

export const useRenameCategory = () =>
  useCatalogMutation((input: { id: string; name: string }) =>
    api<CategoryView[]>(`/admin/categories/${input.id}`, { method: 'PUT', body: { name: input.name } }),
  )

export const useCreateService = () =>
  useCatalogMutation((input: ServiceInput) => api<CategoryView[]>('/admin/services', { method: 'POST', body: input }))

export const useUpdateService = () =>
  useCatalogMutation((input: ServiceInput & { id: string }) =>
    api<CategoryView[]>(`/admin/services/${input.id}`, { method: 'PUT', body: input }),
  )

/* Staff */

export const useStaffProfiles = () =>
  useQuery({ queryKey: adminKeys.staff, queryFn: () => api<StaffView[]>('/admin/staff') })

function useStaffMutation<TInput>(request: (input: TInput) => Promise<StaffView[]>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (staff) => {
      client.setQueryData(adminKeys.staff, staff)
      client.invalidateQueries({ queryKey: adminKeys.branches })
      client.invalidateQueries({ queryKey: adminKeys.catalog })
      client.invalidateQueries({ queryKey: adminKeys.overview })
      // Removing a branch from a profile removes the shifts there.
      client.invalidateQueries({ queryKey: adminKeys.rotas })
      client.invalidateQueries({ queryKey: ['staff', 'admin', 'schedule'] })
    },
  })
}

export const useCreateStaff = () =>
  useStaffMutation((input: StaffInput) => api<StaffView[]>('/admin/staff', { method: 'POST', body: input }))

export const useUpdateStaff = () =>
  useStaffMutation((input: StaffInput & { id: string }) =>
    api<StaffView[]>(`/admin/staff/${input.id}`, { method: 'PUT', body: input }),
  )

/* Schedules */

export const useRota = (date: string, locationId?: string) =>
  useQuery({
    queryKey: adminKeys.rota(date, locationId),
    queryFn: () => api<Rota>(`/admin/schedule?date=${date}${locationId ? `&locationId=${locationId}` : ''}`),
    placeholderData: (previous) => previous,
  })

export const useStaffSchedule = (staffId: string) =>
  useQuery({ queryKey: adminKeys.schedule(staffId), queryFn: () => api<StaffSchedule>(`/admin/staff/${staffId}/schedule`) })

/** Schedule changes redraw the rota and can complete the setup checklist (a bookable person with shifts). */
function useScheduleMutation<TInput>(staffId: string, request: (input: TInput) => Promise<StaffSchedule>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (schedule) => {
      client.setQueryData(adminKeys.schedule(staffId), schedule)
      client.invalidateQueries({ queryKey: adminKeys.rotas })
      client.invalidateQueries({ queryKey: adminKeys.overview })
    },
  })
}

const warningsOf = (path: string, body: unknown) =>
  api<{ warnings: ScheduleWarning[] }>(path, { method: 'POST', body }).then((r) => r.warnings)

export const useCheckWeekly = (staffId: string) =>
  useMutation({ mutationFn: (shifts: WeeklyShift[]) => warningsOf(`/admin/staff/${staffId}/schedule/weekly/check`, { shifts }) })

export const useSaveWeekly = (staffId: string) =>
  useScheduleMutation(staffId, (shifts: WeeklyShift[]) =>
    api<StaffSchedule>(`/admin/staff/${staffId}/schedule/weekly`, { method: 'PUT', body: { shifts } }),
  )

export const useCheckDay = (staffId: string, date: string) =>
  useMutation({
    mutationFn: (intervals: WorkInterval[]) => warningsOf(`/admin/staff/${staffId}/schedule/days/${date}/check`, { intervals }),
  })

export const useSaveDay = (staffId: string, date: string) =>
  useScheduleMutation(staffId, (intervals: WorkInterval[]) =>
    api<StaffSchedule>(`/admin/staff/${staffId}/schedule/days/${date}`, { method: 'PUT', body: { intervals } }),
  )

export const useClearDay = (staffId: string, date: string) =>
  useScheduleMutation(staffId, () => api<StaffSchedule>(`/admin/staff/${staffId}/schedule/days/${date}`, { method: 'DELETE' }))

export const useCheckTimeOff = (staffId: string) =>
  useMutation({ mutationFn: (input: TimeOffInput) => warningsOf(`/admin/staff/${staffId}/time-off/check`, input) })

export const useAddTimeOff = (staffId: string) =>
  useScheduleMutation(staffId, (input: TimeOffInput) =>
    api<StaffSchedule>(`/admin/staff/${staffId}/time-off`, { method: 'POST', body: input }),
  )

export const useRemoveTimeOff = (staffId: string) =>
  useScheduleMutation(staffId, (timeOffId: string) =>
    api<StaffSchedule>(`/admin/staff/${staffId}/time-off/${timeOffId}`, { method: 'DELETE' }),
  )

/* Team */

export const useMembers = () =>
  useQuery({ queryKey: adminKeys.members, queryFn: () => api<MemberView[]>('/admin/team/members') })

export const useTeamInvites = () =>
  useQuery({ queryKey: adminKeys.invites, queryFn: () => api<InviteView[]>('/admin/team/invites') })

export function useInviteTeamMember() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: TeamInviteInput) => api<IssuedInvite>('/admin/team/invites', { method: 'POST', body: input }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: adminKeys.invites })
      client.invalidateQueries({ queryKey: adminKeys.staff })
    },
  })
}

export function useRevokeTeamInvite() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (inviteId: string) => api<void>(`/admin/team/invites/${inviteId}`, { method: 'DELETE' }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: adminKeys.invites })
      client.invalidateQueries({ queryKey: adminKeys.staff })
    },
  })
}

export function useDeactivateMember() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (membershipId: string) =>
      api<MemberView[]>(`/admin/team/members/${membershipId}/deactivate`, { method: 'POST' }),
    onSuccess: (members) => {
      client.setQueryData(adminKeys.members, members)
      client.invalidateQueries({ queryKey: adminKeys.staff })
    },
  })
}
