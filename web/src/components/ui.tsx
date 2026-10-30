import {
  CheckCircle2,
  CircleDashed,
  CircleDot,
  CircleSlash,
  Clock3,
  Copy,
  Check,
  PauseCircle,
  XCircle,
  type LucideIcon,
} from 'lucide-react'
import {
  useId,
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react'
import type { BusinessStatus, InviteStatus, StaffRole } from '../api/types'

/* ------------------------------------------------------------------ Buttons */

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
type ButtonSize = 'sm' | 'md'

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-on-accent shadow-card hover:bg-accent-strong',
  secondary: 'bg-surface text-ink border border-line-strong shadow-card hover:bg-sunken',
  ghost: 'text-muted hover:bg-sunken hover:text-ink',
  danger: 'bg-surface text-danger border border-line-strong hover:bg-danger-soft',
}

const sizes: Record<ButtonSize, string> = {
  sm: 'h-8 gap-1.5 px-3 text-[13px]',
  md: 'h-10 gap-2 px-4 text-sm',
}

export function Button({
  variant = 'primary',
  size = 'md',
  icon: Icon,
  busy = false,
  className = '',
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
  icon?: LucideIcon
  busy?: boolean
}) {
  return (
    <button
      type="button"
      {...props}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      className={`inline-flex shrink-0 items-center justify-center rounded-lg font-medium transition-colors disabled:pointer-events-none disabled:opacity-50 ${variants[variant]} ${sizes[size]} ${className}`}
    >
      {busy ? <Spinner small /> : Icon && <Icon className="size-4" aria-hidden />}
      {children}
    </button>
  )
}

/* ------------------------------------------------------------------- Fields */

const controlStyle =
  'w-full rounded-lg border bg-surface px-3 text-[15px] text-ink placeholder:text-subtle transition-shadow focus:border-accent focus:outline-none focus:ring-4 focus:ring-accent/15 disabled:bg-sunken disabled:text-muted'

interface FieldProps {
  label: string
  hint?: ReactNode
  error?: string
  optional?: boolean
  className?: string
}

function Field({
  id,
  label,
  hint,
  error,
  optional,
  className = '',
  children,
}: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
        {optional && <span className="ml-1 font-normal text-subtle">optional</span>}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-[13px] text-danger">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="text-[13px] text-muted">
            {hint}
          </p>
        )
      )}
    </div>
  )
}

function describedBy(id: string, error?: string, hint?: ReactNode) {
  return error ? `${id}-error` : hint ? `${id}-hint` : undefined
}

export function TextField({
  label,
  hint,
  error,
  optional,
  className,
  prefix,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & FieldProps & { prefix?: string }) {
  const id = useId()
  return (
    <Field id={id} label={label} hint={hint} error={error} optional={optional} className={className}>
      <div className="relative">
        {prefix && (
          <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-muted">{prefix}</span>
        )}
        <input
          id={id}
          {...props}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(id, error, hint)}
          className={`${controlStyle} h-10 ${prefix ? 'pl-7' : ''} ${error ? 'border-danger' : 'border-line-strong'}`}
        />
      </div>
    </Field>
  )
}

export function SelectField({
  label,
  hint,
  error,
  optional,
  className,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & FieldProps) {
  const id = useId()
  return (
    <Field id={id} label={label} hint={hint} error={error} optional={optional} className={className}>
      <select
        id={id}
        {...props}
        aria-describedby={describedBy(id, error, hint)}
        className={`${controlStyle} h-10 border-line-strong pr-8`}
      >
        {children}
      </select>
    </Field>
  )
}

export function TextAreaField({
  label,
  hint,
  error,
  optional,
  className,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & FieldProps) {
  const id = useId()
  return (
    <Field id={id} label={label} hint={hint} error={error} optional={optional} className={className}>
      <textarea
        id={id}
        rows={3}
        {...props}
        aria-describedby={describedBy(id, error, hint)}
        className={`${controlStyle} border-line-strong py-2`}
      />
    </Field>
  )
}

/** On/off switch with a visible label (role="switch"). */
export function Toggle({
  checked,
  onChange,
  label,
  description,
  disabled,
  hideLabel = false,
}: {
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  description?: string
  disabled?: boolean
  /** Keep the label for screen readers only, when the row already shows what the switch is for. */
  hideLabel?: boolean
}) {
  const id = useId()
  return (
    <div className="flex items-start justify-between gap-4">
      <div className={hideLabel ? 'sr-only' : ''}>
        <label htmlFor={id} className="text-sm font-medium">
          {label}
        </label>
        {description && <p className="text-[13px] text-muted">{description}</p>}
      </div>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={`relative mt-0.5 inline-flex h-6 w-10 shrink-0 items-center rounded-full transition-colors disabled:opacity-50 ${
          checked ? 'bg-accent' : 'bg-line-strong'
        }`}
      >
        <span
          className={`inline-block size-5 rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-[18px]' : 'translate-x-0.5'}`}
        />
      </button>
    </div>
  )
}

/** A checkbox row for picking several items, such as branches or services. */
export function CheckboxList<T extends { id: string; label: string; detail?: string }>({
  legend,
  items,
  selected,
  onChange,
  empty,
}: {
  legend: string
  items: T[]
  selected: string[]
  onChange: (ids: string[]) => void
  empty?: string
}) {
  return (
    <fieldset className="flex flex-col gap-1.5">
      <legend className="mb-1.5 text-sm font-medium">{legend}</legend>
      {items.length === 0 && <p className="text-[13px] text-muted">{empty}</p>}
      <div className="flex flex-col divide-y divide-line overflow-hidden rounded-lg border border-line-strong">
        {items.map((item) => {
          const checked = selected.includes(item.id)
          return (
            <label key={item.id} className="flex cursor-pointer items-center gap-3 px-3 py-2.5 hover:bg-sunken/60">
              <input
                type="checkbox"
                checked={checked}
                onChange={() => onChange(checked ? selected.filter((s) => s !== item.id) : [...selected, item.id])}
                className="size-4 rounded accent-[var(--color-accent)]"
              />
              <span className="flex-1 text-sm">{item.label}</span>
              {item.detail && <span className="text-[13px] text-muted">{item.detail}</span>}
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}

/* ------------------------------------------------------------ Layout pieces */

export function Card({
  title,
  description,
  actions,
  children,
  className = '',
  padded = true,
}: {
  title?: ReactNode
  description?: ReactNode
  actions?: ReactNode
  children?: ReactNode
  className?: string
  padded?: boolean
}) {
  return (
    <section className={`rounded-xl border border-line bg-surface shadow-card ${className}`}>
      {(title || actions) && (
        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-5 py-4">
          <div>
            {title && <h2 className="text-[15px] font-semibold">{title}</h2>}
            {description && <p className="mt-0.5 text-[13px] text-muted">{description}</p>}
          </div>
          {actions && <div className="flex gap-2">{actions}</div>}
        </header>
      )}
      <div className={padded ? 'p-5' : ''}>{children}</div>
    </section>
  )
}

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-8 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold">{title}</h1>
        {description && <div className="mt-1 text-muted">{description}</div>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function EmptyState({
  icon: Icon,
  title,
  children,
  action,
}: {
  icon?: LucideIcon
  title: string
  children?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center rounded-xl border border-dashed border-line-strong px-6 py-12 text-center">
      {Icon && (
        <div className="mb-3 flex size-10 items-center justify-center rounded-full bg-sunken text-muted">
          <Icon className="size-5" aria-hidden />
        </div>
      )}
      <p className="font-medium">{title}</p>
      {children && <div className="mt-1 max-w-sm text-sm text-muted">{children}</div>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function Alert({ tone = 'danger', children }: { tone?: 'danger' | 'success' | 'info'; children: ReactNode }) {
  const styles = {
    danger: 'border-danger/20 bg-danger-soft text-danger',
    success: 'border-success/20 bg-success-soft text-ink',
    info: 'border-line bg-sunken text-ink',
  }
  return (
    <div role={tone === 'danger' ? 'alert' : 'status'} className={`rounded-lg border px-4 py-3 text-sm ${styles[tone]}`}>
      {children}
    </div>
  )
}

export function Spinner({ small = false, label }: { small?: boolean; label?: string }) {
  const size = small ? 'size-4' : 'size-5'
  return (
    <span role={label ? 'status' : undefined} className="inline-flex items-center gap-2 text-muted">
      <span className={`${size} animate-spin rounded-full border-2 border-current border-t-transparent`} aria-hidden />
      {label && <span className="text-sm">{label}</span>}
    </span>
  )
}

/** Placeholder blocks while content loads, shaped like what is coming. */
export function Skeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div role="status" aria-label="Loading" className="flex flex-col gap-3">
      {Array.from({ length: lines }, (_, i) => (
        <div key={i} className="h-14 animate-pulse rounded-xl bg-sunken" />
      ))}
    </div>
  )
}

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' }) {
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('')
  return (
    <span
      aria-hidden
      className={`inline-flex shrink-0 items-center justify-center rounded-full bg-accent-soft font-medium text-accent-strong ${
        size === 'sm' ? 'size-7 text-xs' : 'size-10 text-sm'
      }`}
    >
      {initials}
    </span>
  )
}

/* ------------------------------------------------------------------- Badges */

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'accent'

const toneStyles: Record<Tone, string> = {
  neutral: 'bg-sunken text-muted',
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  accent: 'bg-accent-soft text-accent-strong',
}

export function Badge({ tone = 'neutral', icon: Icon, children }: { tone?: Tone; icon?: LucideIcon; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap ${toneStyles[tone]}`}>
      {Icon && <Icon className="size-3.5" aria-hidden />}
      {children}
    </span>
  )
}

const statuses: Record<BusinessStatus | InviteStatus, { label: string; icon: LucideIcon; tone: Tone }> = {
  ONBOARDING: { label: 'Onboarding', icon: CircleDashed, tone: 'warning' },
  LIVE: { label: 'Live', icon: CircleDot, tone: 'success' },
  SUSPENDED: { label: 'Suspended', icon: PauseCircle, tone: 'neutral' },
  CLOSED: { label: 'Closed', icon: XCircle, tone: 'danger' },
  PENDING: { label: 'Pending', icon: Clock3, tone: 'warning' },
  ACCEPTED: { label: 'Accepted', icon: CheckCircle2, tone: 'success' },
  REVOKED: { label: 'Revoked', icon: CircleSlash, tone: 'neutral' },
  EXPIRED: { label: 'Expired', icon: Clock3, tone: 'neutral' },
}

/** Status with an icon and a label, not colour alone (DSH-7). */
export function StatusBadge({ status }: { status: BusinessStatus | InviteStatus }) {
  const s = statuses[status]
  return (
    <Badge tone={s.tone} icon={s.icon}>
      {s.label}
    </Badge>
  )
}

export const roleLabel: Record<StaffRole, string> = {
  OWNER: 'Owner',
  MANAGER: 'Manager',
  RECEPTION: 'Reception',
  STYLIST: 'Stylist',
}

/**
 * A destructive action that asks once, in place: the trigger turns into "Question? Cancel / Confirm".
 * Keeps the decision next to the thing it affects instead of opening a dialog.
 */
export function ConfirmButton({
  label,
  question,
  confirmLabel = label,
  icon,
  iconOnly = false,
  busy,
  onConfirm,
}: {
  label: string
  question: string
  confirmLabel?: string
  icon?: LucideIcon
  iconOnly?: boolean
  busy?: boolean
  onConfirm: () => void
}) {
  const [asking, setAsking] = useState(false)
  if (!asking) {
    return (
      <Button variant="ghost" size="sm" icon={icon} aria-label={iconOnly ? label : undefined} onClick={() => setAsking(true)}>
        {!iconOnly && label}
      </Button>
    )
  }
  return (
    <span role="group" aria-label={question} className="inline-flex items-center gap-2">
      <span className="text-[13px] text-muted">{question}</span>
      <Button variant="ghost" size="sm" onClick={() => setAsking(false)}>
        Cancel
      </Button>
      <Button variant="danger" size="sm" busy={busy} onClick={onConfirm}>
        {confirmLabel}
      </Button>
    </span>
  )
}

/* ------------------------------------------------------------- One-off data */

/** Read-only value with a copy button, for one-time links. */
export function CopyField({ label, value }: { label: string; value: string }) {
  const id = useId()
  const [copied, setCopied] = useState(false)
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <div className="flex gap-2">
        <input
          id={id}
          readOnly
          value={value}
          onFocus={(e) => e.currentTarget.select()}
          className={`${controlStyle} h-10 min-w-0 flex-1 border-line-strong bg-sunken font-mono text-[13px]`}
        />
        <Button
          variant="secondary"
          icon={copied ? Check : Copy}
          onClick={async () => {
            await navigator.clipboard.writeText(value)
            setCopied(true)
          }}
        >
          {copied ? 'Copied' : 'Copy'}
        </Button>
      </div>
    </div>
  )
}

/* --------------------------------------------------------------- Formatting */

/** White text on a brand colour unless it fails WCAG AA (4.5:1); then near-black. Same rule as the Android app. */
export function readableTextOn(hex: string) {
  const value = Number.parseInt(hex.replace('#', ''), 16)
  const channel = (shift: number) => {
    const c = ((value >> shift) & 0xff) / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  }
  const luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
  return 1.05 / (luminance + 0.05) >= 4.5 ? '#ffffff' : '#1a1a1a'
}

export function formatDate(iso: string) {
  return new Intl.DateTimeFormat('en-AU', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

export function formatDay(isoDate: string) {
  return new Intl.DateTimeFormat('en-AU', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }).format(
    new Date(`${isoDate}T00:00:00`),
  )
}

const money = new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' })

/** Whole dollars without cents ("$85"), cents only when needed ("$85.50"). */
export function formatMoney(cents: number) {
  return cents % 100 === 0 ? money.format(cents / 100).replace(/\.00$/, '') : money.format(cents / 100)
}

export function formatDuration(minutes: number) {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h === 0 ? `${m} min` : m === 0 ? `${h} h` : `${h} h ${m} min`
}
