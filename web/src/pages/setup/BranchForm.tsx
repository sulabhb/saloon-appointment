import { useState, type FormEvent } from 'react'
import type { BranchInput } from '../../api/adminTypes'
import { suggestSlug } from '../platform/NewBusinessPage'
import { SelectField, TextField, Toggle } from '../../components/ui'

export const TIME_ZONES = [
  'Australia/Sydney',
  'Australia/Melbourne',
  'Australia/Brisbane',
  'Australia/Adelaide',
  'Australia/Darwin',
  'Australia/Hobart',
  'Australia/Perth',
  'Pacific/Auckland',
]

const STATES = ['NSW', 'VIC', 'QLD', 'SA', 'WA', 'TAS', 'ACT', 'NT']

/** Branch details form, used in the "Add branch" panel and the branch Details tab. */
export function BranchForm({
  id,
  initial,
  withActiveToggle = false,
  onSubmit,
  slugError,
}: {
  id: string
  initial?: Partial<BranchInput>
  withActiveToggle?: boolean
  onSubmit: (input: BranchInput) => void
  slugError?: string
}) {
  const [name, setName] = useState(initial?.name ?? '')
  const [slug, setSlug] = useState(initial?.slug ?? '')
  const [slugEdited, setSlugEdited] = useState(Boolean(initial?.slug))
  const [addressLine1, setAddressLine1] = useState(initial?.addressLine1 ?? '')
  const [addressLine2, setAddressLine2] = useState(initial?.addressLine2 ?? '')
  const [suburb, setSuburb] = useState(initial?.suburb ?? '')
  const [state, setState] = useState(initial?.state ?? 'NSW')
  const [postcode, setPostcode] = useState(initial?.postcode ?? '')
  const [phone, setPhone] = useState(initial?.phone ?? '')
  const [email, setEmail] = useState(initial?.email ?? '')
  const [timeZone, setTimeZone] = useState(initial?.timeZone ?? 'Australia/Sydney')
  const [active, setActive] = useState(initial?.active ?? true)

  const effectiveSlug = slugEdited ? slug : suggestSlug(name)

  function submit(event: FormEvent) {
    event.preventDefault()
    onSubmit({
      name: name.trim(),
      slug: effectiveSlug,
      addressLine1: addressLine1.trim(),
      addressLine2: addressLine2.trim() || undefined,
      suburb: suburb.trim(),
      state,
      postcode: postcode.trim(),
      phone: phone.trim() || undefined,
      email: email.trim() || undefined,
      timeZone,
      active,
    })
  }

  return (
    <form id={id} onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
      <TextField label="Branch name" required className="sm:col-span-2" value={name} onChange={(e) => setName(e.target.value)} />
      <TextField
        label="Link name"
        required
        className="sm:col-span-2"
        value={effectiveSlug}
        error={slugError}
        hint="Used in the branch's booking link."
        onChange={(e) => {
          setSlugEdited(true)
          setSlug(e.target.value.toLowerCase())
        }}
      />
      <TextField label="Street address" required className="sm:col-span-2" value={addressLine1} onChange={(e) => setAddressLine1(e.target.value)} />
      <TextField label="Address line 2" optional className="sm:col-span-2" value={addressLine2} onChange={(e) => setAddressLine2(e.target.value)} />
      <TextField label="Suburb" required value={suburb} onChange={(e) => setSuburb(e.target.value)} />
      <div className="grid grid-cols-2 gap-4">
        <SelectField label="State" value={state} onChange={(e) => setState(e.target.value)}>
          {STATES.map((s) => (
            <option key={s}>{s}</option>
          ))}
        </SelectField>
        <TextField label="Postcode" required inputMode="numeric" value={postcode} onChange={(e) => setPostcode(e.target.value)} />
      </div>
      <TextField label="Phone" optional type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />
      <TextField label="Email" optional type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      <SelectField label="Time zone" className="sm:col-span-2" value={timeZone} onChange={(e) => setTimeZone(e.target.value)}>
        {TIME_ZONES.map((z) => (
          <option key={z}>{z}</option>
        ))}
      </SelectField>
      {withActiveToggle && (
        <div className="sm:col-span-2">
          <Toggle
            label="Open for booking"
            description="Inactive branches are hidden from customers. Their history is kept."
            checked={active}
            onChange={setActive}
          />
        </div>
      )}
    </form>
  )
}
