import { Coffee, Plane, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { useCheckDay, useClearDay, useSaveDay } from '../../api/adminQueries'
import type { RotaDay, WorkInterval } from '../../api/adminTypes'
import { Drawer } from '../../components/Drawer'
import { useToast } from '../../components/Toast'
import { Alert, Button, Toggle } from '../../components/ui'
import { IntervalRows, WarningsNotice, useCheckedSave, type Branch } from './ShiftEditor'
import { intervalsError, longDate, timeRange } from './schedule'

/** Changes one date only: different hours, another branch, or a day off (STF-5). */
export function DayDrawer({
  staffId,
  name,
  day,
  workplaces,
  onEditWeek,
  onClose,
}: {
  staffId: string
  name: string
  day: RotaDay
  /** Branches this person works at that the signed-in person may change. */
  workplaces: Branch[]
  onEditWeek: () => void
  onClose: () => void
}) {
  const toast = useToast()
  const [intervals, setIntervals] = useState<WorkInterval[]>(day.intervals)
  const flow = useCheckedSave(useCheckDay(staffId, day.date), useSaveDay(staffId, day.date), () => {
    toast('Day saved')
    onClose()
  })
  const clear = useClearDay(staffId, day.date)

  const locked = day.intervals.some((iv) => !workplaces.some((b) => b.id === iv.locationId))
  const working = intervals.length > 0
  const error = intervalsError(intervals)
  const change = (next: WorkInterval[]) => {
    setIntervals(next)
    flow.reset()
  }

  return (
    <Drawer
      open
      onClose={onClose}
      title={longDate(day.date)}
      description={`${name} · ${day.override ? 'Changed for this date' : 'Follows the usual week'}`}
      footer={
        locked ? (
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
        ) : (
          <>
            <Button variant="ghost" onClick={onClose}>
              Cancel
            </Button>
            <Button busy={flow.busy} disabled={Boolean(error)} onClick={() => flow.submit(intervals)}>
              {flow.warnings ? 'Save anyway' : 'Save this day'}
            </Button>
          </>
        )
      }
    >
      <div className="flex flex-col gap-5">
        {locked ? (
          <Alert tone="info">This day includes work at a branch you don't manage, so only its manager or the owner can change it.</Alert>
        ) : workplaces.length === 0 ? (
          <Alert tone="info">Choose where {name} works on their staff profile first.</Alert>
        ) : (
          <>
            <Toggle
              label="Working"
              description={working ? 'Only this date changes; the usual week stays as it is.' : 'Day off on this date only.'}
              checked={working}
              onChange={(on) => change(on ? [{ locationId: workplaces[0]!.id, startsAt: '09:00', endsAt: '17:00' }] : [])}
            />
            {working && <IntervalRows label={longDate(day.date)} intervals={intervals} onChange={change} branches={workplaces} />}
            {error && <p className="text-[13px] text-danger">{error}</p>}
          </>
        )}

        {day.timeOff.length > 0 && (
          <ul className="flex flex-col gap-2">
            {day.timeOff.map((t) => (
              <li key={t.id} className="flex items-center gap-2 text-sm text-muted">
                {t.kind === 'LEAVE' ? <Plane className="size-4" aria-hidden /> : <Coffee className="size-4" aria-hidden />}
                {t.kind === 'LEAVE' ? 'On leave' : `Break ${timeRange(t.startsAt!, t.endsAt!)}`}
                {t.reason && ` · ${t.reason}`}
              </li>
            ))}
          </ul>
        )}

        {flow.warnings && <WarningsNotice warnings={flow.warnings} />}
        {(flow.error ?? clear.error) && <Alert>{(flow.error ?? clear.error)!.message}</Alert>}

        <div className="flex flex-wrap gap-2 border-t border-line pt-4">
          {day.override && !locked && (
            <Button
              variant="secondary"
              size="sm"
              icon={RotateCcw}
              busy={clear.isPending}
              onClick={() =>
                clear.mutate(undefined, {
                  onSuccess: () => {
                    toast('Back to the usual week')
                    onClose()
                  },
                })
              }
            >
              Use the usual week
            </Button>
          )}
          <Button variant="ghost" size="sm" onClick={onEditWeek}>
            Edit usual week, leave and breaks
          </Button>
        </div>
      </div>
    </Drawer>
  )
}
