import { useId, useRef, type KeyboardEvent } from 'react'

/** Underlined tabs (WAI-ARIA tabs pattern: arrow keys move between tabs). */
export function Tabs<T extends string>({
  tabs,
  value,
  onChange,
  label,
}: {
  tabs: { id: T; label: string }[]
  value: T
  onChange: (id: T) => void
  label: string
}) {
  const baseId = useId()
  const refs = useRef<(HTMLButtonElement | null)[]>([])

  function onKeyDown(event: KeyboardEvent, index: number) {
    const delta = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
    if (!delta) return
    event.preventDefault()
    const next = (index + delta + tabs.length) % tabs.length
    onChange(tabs[next]!.id)
    refs.current[next]?.focus()
  }

  return (
    <div role="tablist" aria-label={label} className="mb-6 flex gap-6 overflow-x-auto overflow-y-hidden border-b border-line [scrollbar-width:none]">
      {tabs.map((tab, i) => {
        const selected = tab.id === value
        return (
          <button
            key={tab.id}
            ref={(el) => {
              refs.current[i] = el
            }}
            id={`${baseId}-${tab.id}`}
            type="button"
            role="tab"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(tab.id)}
            onKeyDown={(e) => onKeyDown(e, i)}
            className={`-mb-px border-b-2 pb-3 text-sm whitespace-nowrap transition-colors ${
              selected ? 'border-accent font-medium text-ink' : 'border-transparent text-muted hover:text-ink'
            }`}
          >
            {tab.label}
          </button>
        )
      })}
    </div>
  )
}
