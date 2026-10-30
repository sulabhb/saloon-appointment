import { useState, type FormEvent } from 'react'
import { useSetBranchPolicy, useUpdatePolicy } from '../../api/adminQueries'
import type { BookingPolicy, BranchDetail, BranchPolicy } from '../../api/adminTypes'
import { useToast } from '../../components/Toast'
import { Alert, Button, Card, SelectField, TextField } from '../../components/ui'

/** "2 hours", "1 day", "30 minutes", "none". */
export function minutesInWords(minutes: number) {
  if (minutes === 0) return 'none'
  if (minutes % 1440 === 0) return `${minutes / 1440} ${minutes === 1440 ? 'day' : 'days'}`
  if (minutes % 60 === 0) return `${minutes / 60} ${minutes === 60 ? 'hour' : 'hours'}`
  return `${minutes} minutes`
}

const SLOT_CHOICES = [5, 10, 15, 20, 30, 60]
const NOTICE_CHOICES = [0, 30, 60, 120, 240, 720, 1440, 2880]
const HORIZON_CHOICES = [7, 14, 30, 60, 90, 180, 365]
const CUTOFF_CHOICES = [0, 120, 360, 720, 1440, 2880, 4320, 10080]

/** The listed choices, plus the current value if it was set some other way. */
const withCurrent = (choices: number[], current: number) => (choices.includes(current) ? choices : [...choices, current].sort((a, b) => a - b))

/** The business's online booking rules (DSH-17); owners edit, managers read. */
export function BusinessRulesCard({ policy, isOwner }: { policy: BookingPolicy; isOwner: boolean }) {
  const update = useUpdatePolicy()
  const toast = useToast()
  const [draft, setDraft] = useState(policy)
  const set = (change: Partial<BookingPolicy>) => setDraft((d) => ({ ...d, ...change }))
  const dirty = JSON.stringify(draft) !== JSON.stringify(policy)

  function submit(event: FormEvent) {
    event.preventDefault()
    update.mutate(draft, { onSuccess: () => toast('Booking rules saved') })
  }

  return (
    <Card title="Online booking rules" description="What customers can book on the website and in the app. Staff bookings aren't limited by these.">
      <form onSubmit={submit} className="flex flex-col gap-4">
        {update.error && <Alert>{update.error.message}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField label="Start times every" disabled={!isOwner} value={draft.slotIntervalMin} onChange={(e) => set({ slotIntervalMin: Number(e.target.value) })}>
            {SLOT_CHOICES.map((m) => (
              <option key={m} value={m}>
                {m} minutes
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Book at least"
            hint="How long before the start online bookings close."
            disabled={!isOwner}
            value={draft.minNoticeMin}
            onChange={(e) => set({ minNoticeMin: Number(e.target.value) })}
          >
            {withCurrent(NOTICE_CHOICES, draft.minNoticeMin).map((m) => (
              <option key={m} value={m}>
                {m === 0 ? 'Up to the start time' : `${minutesInWords(m)} ahead`}
              </option>
            ))}
          </SelectField>
          <SelectField label="Book up to" disabled={!isOwner} value={draft.horizonDays} onChange={(e) => set({ horizonDays: Number(e.target.value) })}>
            {withCurrent(HORIZON_CHOICES, draft.horizonDays).map((d) => (
              <option key={d} value={d}>
                {d} days ahead
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Change or cancel online until"
            hint="After this, customers are asked to call."
            disabled={!isOwner}
            value={draft.cancelCutoffMin}
            onChange={(e) => set({ cancelCutoffMin: Number(e.target.value) })}
          >
            {withCurrent(CUTOFF_CHOICES, draft.cancelCutoffMin).map((m) => (
              <option key={m} value={m}>
                {m === 0 ? 'The start time' : `${minutesInWords(m)} before`}
              </option>
            ))}
          </SelectField>
          <TextField
            label="Upcoming bookings per customer"
            hint="Stops one person holding many times. Counted by email or mobile."
            type="number"
            min={1}
            max={50}
            disabled={!isOwner}
            value={draft.maxFutureBookingsPerContact}
            onChange={(e) => set({ maxFutureBookingsPerContact: Number(e.target.value) })}
          />
        </div>
        {isOwner ? (
          <div>
            <Button type="submit" busy={update.isPending} disabled={!dirty}>
              Save rules
            </Button>
          </div>
        ) : (
          <p className="text-[13px] text-muted">Only owners can change these.</p>
        )}
      </form>
    </Card>
  )
}

/** A branch's own slot interval, notice or horizon, where it differs from the business (owners only). */
export function BranchRulesCard({ branch, business }: { branch: BranchDetail; business: BookingPolicy }) {
  const update = useSetBranchPolicy(branch.id)
  const toast = useToast()
  const [draft, setDraft] = useState<BranchPolicy>(branch.policy)
  const dirty = JSON.stringify(draft) !== JSON.stringify(branch.policy)
  const pick = (value: string) => (value === '' ? null : Number(value))

  return (
    <Card title="Booking rules for this branch" description="Default means the business setting on the Overview page. Change only what this branch does differently.">
      <form
        onSubmit={(e) => {
          e.preventDefault()
          update.mutate(draft, { onSuccess: () => toast('Branch booking rules saved') })
        }}
        className="flex flex-col gap-4"
      >
        {update.error && <Alert>{update.error.message}</Alert>}
        <div className="grid gap-4 sm:grid-cols-3">
          <SelectField label="Start times every" value={draft.slotIntervalMin ?? ''} onChange={(e) => setDraft((d) => ({ ...d, slotIntervalMin: pick(e.target.value) }))}>
            <option value="">Default ({business.slotIntervalMin} min)</option>
            {SLOT_CHOICES.map((m) => (
              <option key={m} value={m}>
                {m} minutes
              </option>
            ))}
          </SelectField>
          <SelectField label="Book at least" value={draft.minNoticeMin ?? ''} onChange={(e) => setDraft((d) => ({ ...d, minNoticeMin: pick(e.target.value) }))}>
            <option value="">Default ({minutesInWords(business.minNoticeMin)})</option>
            {withCurrent(NOTICE_CHOICES, draft.minNoticeMin ?? 0).map((m) => (
              <option key={m} value={m}>
                {m === 0 ? 'Up to the start time' : `${minutesInWords(m)} ahead`}
              </option>
            ))}
          </SelectField>
          <SelectField label="Book up to" value={draft.horizonDays ?? ''} onChange={(e) => setDraft((d) => ({ ...d, horizonDays: pick(e.target.value) }))}>
            <option value="">Default ({business.horizonDays} days)</option>
            {withCurrent(HORIZON_CHOICES, draft.horizonDays ?? HORIZON_CHOICES[0]!).map((d) => (
              <option key={d} value={d}>
                {d} days ahead
              </option>
            ))}
          </SelectField>
        </div>
        <div>
          <Button type="submit" variant="secondary" busy={update.isPending} disabled={!dirty}>
            Save branch rules
          </Button>
        </div>
      </form>
    </Card>
  )
}
