import { useBookingOptions, useStaffSlots } from '../../api/bookingQueries'
import { Alert, SelectField, Skeleton, TextField } from '../../components/ui'
import { branchClock } from './time'

/**
 * Stylist, date and start time chosen together (DSH-9). With "anyone", each time lists who is
 * free, and the server picks among them. Controlled: the parent keeps the choices.
 */
export function SlotPicker({
  locationId,
  serviceIds,
  date,
  onDate,
  staffId,
  onStaff,
  start,
  onStart,
  reschedule,
  lockDate = false,
}: {
  locationId: string
  serviceIds: string[]
  date: string
  onDate: (date: string) => void
  staffId: string | null
  onStaff: (staffId: string | null) => void
  start: string | null
  onStart: (start: string) => void
  reschedule?: string
  lockDate?: boolean
}) {
  const options = useBookingOptions(locationId)
  const slots = useStaffSlots(locationId, serviceIds, staffId, date, reschedule)
  const qualified = (options.data?.stylists ?? []).filter((s) => serviceIds.every((id) => s.serviceIds.includes(id)))
  const name = (id: string) => options.data?.stylists.find((s) => s.id === id)?.displayName ?? ''

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3">
        <SelectField label="Stylist" value={staffId ?? ''} onChange={(e) => onStaff(e.target.value || null)}>
          <option value="">Anyone free</option>
          {qualified.map((s) => (
            <option key={s.id} value={s.id}>
              {s.displayName}
            </option>
          ))}
        </SelectField>
        <TextField label="Date" type="date" value={date} disabled={lockDate} onChange={(e) => e.target.value && onDate(e.target.value)} />
      </div>
      {serviceIds.length === 0 && <p className="text-sm text-muted">Choose services to see free times.</p>}
      {slots.isPending && serviceIds.length > 0 && <Skeleton lines={2} />}
      {slots.error && <Alert>{slots.error.message}</Alert>}
      {slots.data && slots.data.slots.length === 0 && (
        <p className="rounded-lg bg-sunken px-3 py-2.5 text-sm text-muted">
          No free times on this day{staffId ? ` for ${name(staffId)}` : ''}. Try another day{staffId ? ' or anyone free' : ''}.
        </p>
      )}
      {slots.data && slots.data.slots.length > 0 && (
        <div role="radiogroup" aria-label="Start time" className="grid max-h-72 grid-cols-3 gap-2 overflow-y-auto pr-1">
          {slots.data.slots.map((s) => {
            const selected = start === s.start
            const who = s.staffIds.map(name).filter(Boolean).join(', ')
            return (
              <button
                key={s.start}
                type="button"
                role="radio"
                aria-checked={selected}
                aria-label={`${branchClock(s.start, slots.data.timeZone)}${staffId ? '' : `, free: ${who}`}`}
                onClick={() => onStart(s.start)}
                className={`flex flex-col items-center rounded-lg border px-2 py-2 text-sm transition-colors duration-150 ease-out active:scale-[0.97] ${
                  selected ? 'border-accent bg-accent text-on-accent' : 'border-line-strong hover:bg-sunken'
                }`}
              >
                <span className="font-medium tabular-nums">{branchClock(s.start, slots.data.timeZone)}</span>
                {!staffId && <span className={`w-full truncate text-xs ${selected ? 'opacity-85' : 'text-muted'}`}>{who}</span>}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
