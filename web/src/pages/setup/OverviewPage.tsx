import { ArrowRight, CheckCircle2, Circle, PartyPopper, Rocket } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { useGoLive, useOverview, useUpdateProfile } from '../../api/adminQueries'
import type { BusinessOverview } from '../../api/adminTypes'
import { useMe } from '../../api/queries'
import { useToast } from '../../components/Toast'
import { Alert, Button, Card, PageHeader, Skeleton, StatusBadge, TextAreaField, TextField, readableTextOn } from '../../components/ui'
import { BusinessRulesCard } from './BookingRules'

/** Owner's start page: what is left before going live (DSH-18), and the business profile (TEN-7). */
export function OverviewPage() {
  const overview = useOverview()
  const isOwner = useMe().data?.selected?.role === 'OWNER'

  if (overview.isPending) return <Skeleton lines={4} />
  if (overview.error) return <Alert>{overview.error.message}</Alert>

  const { profile } = overview.data
  return (
    <>
      <PageHeader
        title={profile.displayName}
        description={
          <span className="inline-flex flex-wrap items-center gap-2">
            <StatusBadge status={profile.status} />
            <span className="text-sm">
              Salon code <span className="font-mono text-ink">{profile.slug}</span>
            </span>
          </span>
        }
      />
      <div className="grid gap-6 lg:grid-cols-5">
        <div className="flex min-w-0 flex-col gap-6 lg:col-span-3">
          <Checklist overview={overview.data} isOwner={isOwner} />
          <BusinessRulesCard key={JSON.stringify(overview.data.policy)} policy={overview.data.policy} isOwner={isOwner} />
        </div>
        <div className="min-w-0 lg:col-span-2">
          <ProfileCard overview={overview.data} isOwner={isOwner} />
        </div>
      </div>
    </>
  )
}

function Checklist({ overview, isOwner }: { overview: BusinessOverview; isOwner: boolean }) {
  const goLive = useGoLive()
  const toast = useToast()
  const { setup, profile } = overview
  const steps = [
    {
      done: setup.branchWithHours,
      title: 'Add a branch with opening hours',
      text: 'Where customers come, and when you are open.',
      to: '/branches',
      cta: 'Branches',
    },
    {
      done: setup.serviceOffered,
      title: 'Offer a service at that branch',
      text: 'Name, duration and price customers see when booking.',
      to: '/services',
      cta: 'Services',
    },
    {
      done: setup.bookableStaff,
      title: 'Add a stylist and their hours',
      text: 'Assign them to the branch and its services, then set the days they work there.',
      to: setup.serviceOffered ? '/schedule' : '/staff',
      cta: setup.serviceOffered ? 'Schedule' : 'Staff',
    },
  ]
  const doneCount = steps.filter((s) => s.done).length

  if (setup.status === 'LIVE') {
    return (
      <Card>
        <div className="flex items-start gap-4">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-success-soft text-success">
            <PartyPopper className="size-5" aria-hidden />
          </div>
          <div>
            <h2 className="font-semibold">You are live</h2>
            <p className="mt-1 text-sm text-muted">
              Customers can open your salon with the code <span className="font-mono text-ink">{profile.slug}</span> in
              the app or on the booking site.
            </p>
          </div>
        </div>
      </Card>
    )
  }

  return (
    <Card
      title="Get ready to take bookings"
      description={`${doneCount} of ${steps.length} steps done`}
      padded={false}
    >
      <div className="px-5 pt-4">
        <div className="h-1.5 overflow-hidden rounded-full bg-sunken" role="progressbar" aria-valuemin={0} aria-valuemax={3} aria-valuenow={doneCount} aria-label="Setup progress">
          <div className="h-full rounded-full bg-accent transition-all" style={{ width: `${(doneCount / steps.length) * 100}%` }} />
        </div>
      </div>
      <ol className="divide-y divide-line">
        {steps.map((step) => (
          <li key={step.title} className="flex items-center gap-4 px-5 py-4">
            {step.done ? (
              <CheckCircle2 className="size-5 shrink-0 text-success" aria-label="Done" />
            ) : (
              <Circle className="size-5 shrink-0 text-line-strong" aria-label="To do" />
            )}
            <div className="min-w-0 flex-1">
              <p className={`text-sm font-medium ${step.done ? 'text-muted line-through decoration-line-strong' : ''}`}>
                {step.title}
              </p>
              <p className="text-[13px] text-muted">{step.text}</p>
            </div>
            <Link
              to={step.to}
              className="inline-flex shrink-0 items-center gap-1 rounded-lg px-2.5 py-1.5 text-[13px] font-medium text-accent hover:bg-accent-soft"
            >
              {step.cta}
              <ArrowRight className="size-3.5" aria-hidden />
            </Link>
          </li>
        ))}
      </ol>
      {setup.status === 'ONBOARDING' && (
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line bg-sunken/50 px-5 py-4">
          <p className="text-[13px] text-muted">
            {setup.canGoLive ? 'Everything is in place.' : 'Going live opens online booking to customers.'}
          </p>
          {isOwner && (
            <Button
              icon={Rocket}
              disabled={!setup.canGoLive}
              busy={goLive.isPending}
              onClick={() => goLive.mutate(undefined, { onSuccess: () => toast('Your salon is live') })}
            >
              Go live
            </Button>
          )}
        </div>
      )}
      {goLive.error && (
        <div className="px-5 pb-4">
          <Alert>{goLive.error.message}</Alert>
        </div>
      )}
    </Card>
  )
}

function ProfileCard({ overview, isOwner }: { overview: BusinessOverview; isOwner: boolean }) {
  const update = useUpdateProfile()
  const toast = useToast()
  const { profile } = overview
  const [displayName, setDisplayName] = useState(profile.displayName)
  const [description, setDescription] = useState(profile.description ?? '')
  const [accentColor, setAccentColor] = useState(profile.accentColor ?? '#4f6f5c')
  const [contactEmail, setContactEmail] = useState(profile.contactEmail ?? '')

  function submit(event: FormEvent) {
    event.preventDefault()
    update.mutate(
      { displayName, description, accentColor, contactEmail: contactEmail || undefined },
      { onSuccess: () => toast('Profile saved') },
    )
  }

  return (
    <Card title="Profile" description="What customers see on your booking pages.">
      <form onSubmit={submit} className="flex flex-col gap-4">
        {update.error && <Alert>{update.error.message}</Alert>}
        <TextField label="Salon name" value={displayName} disabled={!isOwner} onChange={(e) => setDisplayName(e.target.value)} />
        <TextAreaField
          label="Short description"
          optional
          maxLength={500}
          value={description}
          disabled={!isOwner}
          onChange={(e) => setDescription(e.target.value)}
        />
        <TextField
          label="Contact email"
          type="email"
          optional
          value={contactEmail}
          disabled={!isOwner}
          onChange={(e) => setContactEmail(e.target.value)}
        />
        <div className="flex flex-col gap-1.5">
          <label htmlFor="brand" className="text-sm font-medium">
            Brand colour
          </label>
          <div className="flex items-center gap-3">
            <input
              id="brand"
              type="color"
              value={accentColor}
              disabled={!isOwner}
              onChange={(e) => setAccentColor(e.target.value)}
              className="h-10 w-14 cursor-pointer rounded-lg border border-line-strong bg-surface p-1"
            />
            <span
              className="inline-flex h-8 items-center rounded-lg px-3 text-[13px] font-medium"
              style={{ backgroundColor: accentColor, color: readableTextOn(accentColor) }}
            >
              Book now
            </span>
            <span className="font-mono text-[13px] text-muted">{accentColor.toUpperCase()}</span>
          </div>
        </div>
        {isOwner && (
          <div>
            <Button type="submit" busy={update.isPending}>
              Save profile
            </Button>
          </div>
        )}
      </form>
    </Card>
  )
}
