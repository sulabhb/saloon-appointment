import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { useCreateBusiness } from '../../api/queries'
import type { CreatedBusiness } from '../../api/types'
import { Alert, Button, Card, CopyField, PageHeader, TextField } from '../../components/ui'

const TIME_ZONES = [
  'Australia/Sydney',
  'Australia/Melbourne',
  'Australia/Brisbane',
  'Australia/Adelaide',
  'Australia/Darwin',
  'Australia/Hobart',
  'Australia/Perth',
  'Pacific/Auckland',
]

/** Same rule as the backend: 3–50 lower-case letters, digits and dashes, not starting or ending with a dash. */
const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$/

export function suggestSlug(name: string) {
  return name
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 50)
}

export function NewBusinessPage() {
  const create = useCreateBusiness()
  const [displayName, setDisplayName] = useState('')
  const [slug, setSlug] = useState('')
  const [slugEdited, setSlugEdited] = useState(false)
  const [legalName, setLegalName] = useState('')
  const [timeZone, setTimeZone] = useState('Australia/Sydney')
  const [ownerEmail, setOwnerEmail] = useState('')
  const [accentColor, setAccentColor] = useState('#5e7a68')
  const [description, setDescription] = useState('')
  const [created, setCreated] = useState<CreatedBusiness | null>(null)

  const effectiveSlug = slugEdited ? slug : suggestSlug(displayName)
  const slugTaken = create.error instanceof ApiError && create.error.code === 'SLUG_TAKEN'
  const slugInvalid = effectiveSlug.length > 0 && !SLUG_PATTERN.test(effectiveSlug)
  const canSubmit = displayName.trim() && legalName.trim() && ownerEmail.includes('@') && SLUG_PATTERN.test(effectiveSlug)

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate(
      {
        slug: effectiveSlug,
        displayName: displayName.trim(),
        legalName: legalName.trim(),
        defaultTimeZone: timeZone,
        ownerEmail: ownerEmail.trim(),
        accentColor,
        description: description.trim() || undefined,
      },
      { onSuccess: setCreated },
    )
  }

  if (created) {
    return (
      <>
        <PageHeader title={`${created.business.displayName} created`} description="It stays in Onboarding until it is set live." />
        <Card className="flex max-w-2xl flex-col gap-4">
          <p>
            Send this link to <strong>{created.ownerInvite.email}</strong>. It lets them create their owner account and
            works once, for 7 days.
          </p>
          <CopyField label="Owner invite link" value={created.ownerInvite.url} />
          <Alert tone="info">This link is shown only now. If it is lost, send a new invite from the business page.</Alert>
          <div className="flex flex-wrap gap-2">
            <Link
              to={`/platform/businesses/${created.business.id}`}
              className="inline-flex min-h-11 items-center rounded-full bg-accent px-5 text-sm font-medium text-on-accent"
            >
              View business
            </Link>
            <Button variant="secondary" onClick={() => window.location.reload()}>
              Create another
            </Button>
          </div>
        </Card>
      </>
    )
  }

  return (
    <>
      <PageHeader title="New business" description="Creates the business and an invite for its owner." />
      <Card className="max-w-2xl">
        <form onSubmit={submit} className="grid gap-5 sm:grid-cols-2" noValidate>
          {create.error && !slugTaken && (
            <div className="sm:col-span-2">
              <Alert>{create.error.message}</Alert>
            </div>
          )}
          <TextField label="Salon name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
          <TextField
            label="Salon code"
            value={effectiveSlug}
            hint="Customers type this in the app and see it in booking links."
            error={slugTaken ? 'That salon code is already in use.' : slugInvalid ? 'Use 3–50 lower-case letters, numbers or dashes.' : undefined}
            onChange={(e) => {
              setSlugEdited(true)
              setSlug(e.target.value.toLowerCase())
            }}
          />
          <TextField label="Legal name" value={legalName} onChange={(e) => setLegalName(e.target.value)} />
          <div className="flex flex-col gap-1.5">
            <label htmlFor="tz" className="text-sm font-medium">
              Main time zone
            </label>
            <select
              id="tz"
              value={timeZone}
              onChange={(e) => setTimeZone(e.target.value)}
              className="min-h-11 rounded-xl border border-line bg-surface px-3"
            >
              {TIME_ZONES.map((z) => (
                <option key={z}>{z}</option>
              ))}
            </select>
          </div>
          <TextField
            label="Owner email"
            type="email"
            className="sm:col-span-2"
            value={ownerEmail}
            onChange={(e) => setOwnerEmail(e.target.value)}
          />
          <div className="flex flex-col gap-1.5">
            <label htmlFor="accent" className="text-sm font-medium">
              Brand colour
            </label>
            <div className="flex items-center gap-3">
              <input
                id="accent"
                type="color"
                value={accentColor}
                onChange={(e) => setAccentColor(e.target.value)}
                className="h-11 w-16 cursor-pointer rounded-xl border border-line bg-surface"
              />
              <span className="font-mono text-sm text-muted">{accentColor.toUpperCase()}</span>
            </div>
          </div>
          <div className="flex flex-col gap-1.5 sm:col-span-2">
            <label htmlFor="description" className="text-sm font-medium">
              Short description <span className="font-normal text-muted">(optional)</span>
            </label>
            <textarea
              id="description"
              rows={2}
              maxLength={500}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="rounded-xl border border-line bg-surface px-3.5 py-2.5"
            />
          </div>
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit" busy={create.isPending} disabled={!canSubmit}>
              Create and invite owner
            </Button>
            <Link to="/platform" className="inline-flex min-h-11 items-center rounded-full px-5 text-sm hover:bg-sunken">
              Cancel
            </Link>
          </div>
        </form>
      </Card>
    </>
  )
}
