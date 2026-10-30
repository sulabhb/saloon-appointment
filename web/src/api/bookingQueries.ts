import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AppointmentDetail, BookingOptions, CustomerMatch, DayCalendar, NewStaffBooking, StaffSlots } from './bookingTypes'
import { api } from './client'
import type { AppointmentStatus } from './types'

// Under 'staff' so switching business drops them.
export const bookingKeys = {
  calendars: ['staff', 'calendar'] as const,
  calendar: (locationId: string, date: string) => ['staff', 'calendar', locationId, date] as const,
  appointment: (id: string) => ['staff', 'appointment', id] as const,
  options: (locationId: string) => ['staff', 'booking-options', locationId] as const,
}

export const useDayCalendar = (locationId: string | undefined, date: string) =>
  useQuery({
    queryKey: bookingKeys.calendar(locationId ?? '', date),
    queryFn: () => api<DayCalendar>(`/staff/calendar?locationId=${locationId}&date=${date}`),
    enabled: Boolean(locationId),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  })

export const useWeekCalendar = (locationId: string | undefined, date: string, enabled: boolean) =>
  useQuery({
    queryKey: [...bookingKeys.calendars, 'week', locationId ?? '', date],
    queryFn: () => api<DayCalendar[]>(`/staff/calendar/week?locationId=${locationId}&date=${date}`),
    enabled: enabled && Boolean(locationId),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  })

export const useAppointment = (id: string | null) =>
  useQuery({
    queryKey: bookingKeys.appointment(id ?? ''),
    queryFn: () => api<AppointmentDetail>(`/staff/appointments/${id}`),
    enabled: Boolean(id),
  })

export const useBookingOptions = (locationId: string | undefined) =>
  useQuery({
    queryKey: bookingKeys.options(locationId ?? ''),
    queryFn: () => api<BookingOptions>(`/staff/booking-options?locationId=${locationId}`),
    enabled: Boolean(locationId),
  })

export const useStaffSlots = (
  locationId: string,
  serviceIds: string[],
  staffId: string | null,
  date: string,
  reschedule?: string,
) =>
  useQuery({
    queryKey: ['staff', 'slots', locationId, serviceIds, staffId, date, reschedule ?? null],
    queryFn: () =>
      api<StaffSlots>(
        `/staff/availability?locationId=${locationId}&services=${serviceIds.join(',')}&date=${date}` +
          (staffId ? `&staffId=${staffId}` : '') +
          (reschedule ? `&reschedule=${reschedule}` : ''),
      ),
    enabled: serviceIds.length > 0 && Boolean(date),
  })

export const useCustomerSearch = (q: string) =>
  useQuery({
    queryKey: ['staff', 'customers', q],
    queryFn: () => api<CustomerMatch[]>(`/staff/customers?q=${encodeURIComponent(q)}`),
    enabled: q.trim().length >= 2,
    placeholderData: keepPreviousData,
  })

/** Any change redraws calendars and agendas and refreshes the appointment itself. */
function useAppointmentMutation<TInput>(request: (input: TInput) => Promise<AppointmentDetail>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: request,
    onSuccess: (detail) => {
      client.setQueryData(bookingKeys.appointment(detail.id), detail)
      client.invalidateQueries({ queryKey: bookingKeys.calendars })
      client.invalidateQueries({ queryKey: ['staff', 'agenda'] })
      client.invalidateQueries({ queryKey: ['staff', 'slots'] })
    },
  })
}

export const useCreateStaffBooking = () =>
  useAppointmentMutation((input: NewStaffBooking) => api<AppointmentDetail>('/staff/appointments', { method: 'POST', body: input }))

export const useChangeStatus = (id: string) =>
  useAppointmentMutation((input: { status: AppointmentStatus; reason?: string }) =>
    api<AppointmentDetail>(`/staff/appointments/${id}/status`, { method: 'POST', body: input }),
  )

export const useReschedule = (id: string) =>
  useAppointmentMutation((input: { start: string; staffId: string | null }) =>
    api<AppointmentDetail>(`/staff/appointments/${id}/reschedule`, { method: 'POST', body: input }),
  )

export const useSaveInternalNote = (id: string) =>
  useAppointmentMutation((internalNote: string) =>
    api<AppointmentDetail>(`/staff/appointments/${id}/note`, { method: 'PUT', body: { internalNote } }),
  )
