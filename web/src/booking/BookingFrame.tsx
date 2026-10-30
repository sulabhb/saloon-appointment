import { ChevronLeft } from 'lucide-react'
import { useEffect, type CSSProperties, type ReactNode } from 'react'
import { readableTextOn } from '../components/ui'
import { FALLBACK_BRAND } from './format'

/**
 * The booking site's frame: white paper, one ink, and the salon's own colour as the only colour.
 * The colour reaches children as --brand and --on-brand.
 */
export function BookingFrame({ accentColor, children }: { accentColor: string | null; children: ReactNode }) {
  const brand = accentColor ?? FALLBACK_BRAND
  const style = { '--brand': brand, '--on-brand': readableTextOn(brand) } as CSSProperties
  return (
    <div style={style} className="min-h-dvh bg-white font-booking text-[15px] leading-6 text-[#1C1B1A] [color-scheme:light]">
      {children}
    </div>
  )
}

/** The salon's name, set large: the first thing a customer should recognise. */
export function SalonHeader({
  name,
  branchLine,
  onChangeBranch,
  onBack,
}: {
  name: string
  branchLine?: string
  onChangeBranch?: () => void
  onBack?: () => void
}) {
  // The tab should name the salon, not the platform.
  useEffect(() => {
    const previous = document.title
    document.title = `${name}: book online`
    return () => {
      document.title = previous
    }
  }, [name])
  return (
    <header className="mx-auto max-w-[34rem] px-5 pt-6 pb-5 sm:pt-12">
      <div className="mb-6 h-9">
        {onBack && (
          <button
            type="button"
            onClick={onBack}
            className="-ml-2 inline-flex h-9 items-center gap-1 rounded-lg px-2 text-sm text-[#6E6A65] hover:bg-[#F3F2F0] hover:text-[#1C1B1A]"
          >
            <ChevronLeft className="size-4" aria-hidden />
            Back
          </button>
        )}
      </div>
      <p className="text-[2.25rem] leading-[1.05] font-semibold text-balance sm:text-[2.75rem]">{name}</p>
      {branchLine && (
        <p className="mt-2 flex flex-wrap items-baseline gap-x-3 text-[#6E6A65]">
          <span>{branchLine}</span>
          {onChangeBranch && (
            <button type="button" onClick={onChangeBranch} className="text-sm font-medium text-[#1C1B1A] underline underline-offset-4">
              Change branch
            </button>
          )}
        </p>
      )}
    </header>
  )
}

/** "Step 2 of 5" with a thin progress rule in the salon's colour; the steps really are a sequence. */
export function StepHeading({ step, of, title }: { step: number; of: number; title: string }) {
  return (
    <div className="mb-5">
      <div className="mb-4 h-[3px] w-full rounded-full bg-[#EDEBE8]" aria-hidden>
        <div className="h-full rounded-full bg-(--brand) transition-[width] duration-300 ease-out" style={{ width: `${(step / of) * 100}%` }} />
      </div>
      <p className="text-sm text-[#6E6A65]">
        Step {step} of {of}
      </p>
      <h1 className="mt-0.5 text-[1.375rem] leading-tight font-semibold">{title}</h1>
    </div>
  )
}

/**
 * The always-visible summary and next action (BKG-4), in the salon's colour. Fixed to the
 * bottom of the screen, clear of the iPhone home indicator.
 */
export function ActionBar({
  summary,
  detail,
  action,
  onAction,
  disabled,
  busy,
}: {
  summary: ReactNode
  detail?: ReactNode
  action: string
  onAction: () => void
  disabled?: boolean
  busy?: boolean
}) {
  return (
    <>
      <div className="h-28" aria-hidden />
      <div className="fixed inset-x-0 bottom-0 z-30 bg-(--brand) text-(--on-brand) shadow-[0_-8px_24px_rgba(0,0,0,0.12)]">
        <div className="mx-auto flex max-w-[34rem] items-center gap-4 px-5 pt-3.5 pb-[calc(env(safe-area-inset-bottom)+14px)]">
          <div className="min-w-0 flex-1" aria-live="polite">
            <p className="truncate font-semibold tabular-nums">{summary}</p>
            {detail && <p className="truncate text-sm tabular-nums opacity-80">{detail}</p>}
          </div>
          <button
            type="button"
            onClick={onAction}
            disabled={disabled || busy}
            aria-busy={busy}
            className="h-12 shrink-0 rounded-full bg-(--on-brand) px-6 font-semibold text-(--brand) transition-transform duration-150 ease-out active:scale-[0.97] disabled:opacity-50 disabled:active:scale-100"
          >
            {busy ? 'One moment…' : action}
          </button>
        </div>
      </div>
    </>
  )
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'error'; children: ReactNode }) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`rounded-xl px-4 py-3 text-sm ${tone === 'error' ? 'bg-[#FBEDEC] text-[#8A2A21]' : 'bg-[#F3F2F0] text-[#1C1B1A]'}`}
    >
      {children}
    </div>
  )
}
