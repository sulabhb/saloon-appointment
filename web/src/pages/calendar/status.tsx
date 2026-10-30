import { CheckCircle2, CircleDot, LogIn, UserX, XCircle, type LucideIcon } from 'lucide-react'
import type { AppointmentStatus } from '../../api/types'
import { Badge } from '../../components/ui'

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'accent'

export const statusMeta: Record<AppointmentStatus, { label: string; icon: LucideIcon; tone: Tone }> = {
  CONFIRMED: { label: 'Confirmed', icon: CircleDot, tone: 'accent' },
  CHECKED_IN: { label: 'Checked in', icon: LogIn, tone: 'success' },
  COMPLETED: { label: 'Completed', icon: CheckCircle2, tone: 'neutral' },
  CANCELLED: { label: 'Cancelled', icon: XCircle, tone: 'neutral' },
  NO_SHOW: { label: 'No-show', icon: UserX, tone: 'danger' },
}

/** Icon and label as well as colour (DSH-7). */
export function AppointmentStatusBadge({ status }: { status: AppointmentStatus }) {
  const meta = statusMeta[status]
  return (
    <Badge tone={meta.tone} icon={meta.icon}>
      {meta.label}
    </Badge>
  )
}

/** The button label for moving to a status, given where it comes from. */
export function actionLabel(from: AppointmentStatus, to: AppointmentStatus) {
  if (to === 'CHECKED_IN') return from === 'COMPLETED' ? 'Undo complete' : 'Check in'
  if (to === 'CONFIRMED') return 'Undo no-show'
  if (to === 'COMPLETED') return 'Complete'
  if (to === 'NO_SHOW') return 'No-show'
  return 'Cancel appointment'
}
