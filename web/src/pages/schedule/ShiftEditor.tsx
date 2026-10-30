import type { UseMutationResult } from '@tanstack/react-query'
import { AlertTriangle, Plus, X } from 'lucide-react'
import { useState } from 'react'
import type { ScheduleWarning, StaffSchedule, WorkInterval } from '../../api/adminTypes'
import { addShift } from './schedule'

export type Branch = { id: string; name: string }

const timeInput = 'h-9 rounded-lg border border-line-strong bg-surface px-2.5 text-sm tabular-nums'

/** One day's shifts: branch (when there is a choice), start and end, with add and remove. */
export function IntervalRows({
  label,
  intervals,
  onChange,
  branches,
}: {
  label: string
  intervals: WorkInterval[]
  onChange: (intervals: WorkInterval[]) => void
  branches: Branch[]
}) {
  const set = (i: number, change: Partial<WorkInterval>) =>
    onChange(intervals.map((iv, j) => (j === i ? { ...iv, ...change } : iv)))
  return (
    <div className="flex flex-col gap-2">
      {intervals.map((iv, i) => {
        const last = i === intervals.length - 1
        const splitsLunch = iv.startsAt.slice(0, 5) < '12:30' && iv.endsAt.slice(0, 5) > '13:30'
        return (
          <div key={i} className="flex flex-wrap items-center gap-2">
            {branches.length > 1 && (
              <select
                aria-label={`${label} branch`}
                value={iv.locationId}
                onChange={(e) => set(i, { locationId: e.target.value })}
                className={`${timeInput} max-w-44 pr-7`}
              >
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            )}
            <input
              type="time"
              aria-label={`${label} starts`}
              value={iv.startsAt.slice(0, 5)}
              onChange={(e) => set(i, { startsAt: e.target.value })}
              className={timeInput}
            />
            <span className="text-muted">–</span>
            <input
              type="time"
              aria-label={`${label} ends`}
              value={iv.endsAt.slice(0, 5)}
              onChange={(e) => set(i, { endsAt: e.target.value })}
              className={timeInput}
            />
            {intervals.length > 1 && (
              <button
                type="button"
                aria-label="Remove shift"
                onClick={() => onChange(intervals.filter((_, j) => j !== i))}
                className="rounded-lg p-1.5 text-muted hover:bg-sunken hover:text-ink"
              >
                <X className="size-4" aria-hidden />
              </button>
            )}
            {last && (
              <button
                type="button"
                onClick={() => onChange(addShift(intervals, iv))}
                className="inline-flex items-center gap-1 rounded-lg px-2 py-1.5 text-[13px] font-medium text-accent hover:bg-accent-soft"
              >
                <Plus className="size-3.5" aria-hidden />
                {splitsLunch ? 'Add lunch break' : 'Add shift'}
              </button>
            )}
          </div>
        )
      })}
    </div>
  )
}

/** Warnings to read before saving (STF-7). Saving anyway is allowed. */
export function WarningsNotice({ warnings }: { warnings: ScheduleWarning[] }) {
  return (
    <div role="alert" className="rounded-lg border border-warning/30 bg-warning-soft px-4 py-3 text-sm text-ink">
      <p className="flex items-center gap-2 font-medium">
        <AlertTriangle className="size-4 text-warning" aria-hidden />
        Check before saving
      </p>
      <ul className="mt-2 flex list-disc flex-col gap-1 pl-6">
        {warnings.map((w) => (
          <li key={w.message}>{w.message}</li>
        ))}
      </ul>
      <p className="mt-2 text-[13px] text-muted">You can still save if this is intended.</p>
    </div>
  )
}

/**
 * Saves after a server check: with no warnings it saves straight away, otherwise it shows them
 * and the next submit saves anyway. Any edit should call reset() so stale warnings disappear.
 */
export function useCheckedSave<T>(
  check: UseMutationResult<ScheduleWarning[], Error, T>,
  save: UseMutationResult<StaffSchedule, Error, T>,
  onSaved: () => void,
) {
  const [warnings, setWarnings] = useState<ScheduleWarning[] | null>(null)
  const submit = (input: T) => {
    if (warnings) {
      save.mutate(input, { onSuccess: onSaved })
      return
    }
    check.mutate(input, {
      onSuccess: (found) => (found.length > 0 ? setWarnings(found) : save.mutate(input, { onSuccess: onSaved })),
    })
  }
  const reset = () => {
    setWarnings(null)
    check.reset()
    save.reset()
  }
  return { warnings, submit, reset, busy: check.isPending || save.isPending, error: check.error ?? save.error }
}
