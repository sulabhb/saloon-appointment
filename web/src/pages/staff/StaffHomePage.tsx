import { MapPin, Store } from 'lucide-react'
import { useMe, useSelectBusiness, useStaffLocations } from '../../api/queries'
import { Alert, Button, Card, EmptyState, PageHeader, Skeleton, roleLabel } from '../../components/ui'
import { Agenda } from './Agenda'

/** Start page for everyone in a business: the day's appointments and their branches. */
export function StaffHomePage() {
  const me = useMe().data!
  const selected = me.selected
  const locations = useStaffLocations(selected !== null)
  const select = useSelectBusiness()

  if (me.businesses.length === 0) {
    return <EmptyState icon={Store} title="No business yet">Your account is not part of a business.</EmptyState>
  }

  if (!selected) {
    return (
      <>
        <PageHeader title="Choose a business" description="You work in more than one. You can switch at any time from the sidebar." />
        {select.error && <Alert>{select.error.message}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          {me.businesses.map((b) => (
            <Card key={b.businessId}>
              <div className="flex items-center justify-between gap-4">
                <div>
                  <p className="font-semibold">{b.displayName}</p>
                  <p className="text-sm text-muted">{roleLabel[b.role]}</p>
                </div>
                <Button busy={select.isPending && select.variables === b.businessId} onClick={() => select.mutate(b.businessId)}>
                  Open
                </Button>
              </div>
            </Card>
          ))}
        </div>
      </>
    )
  }

  const business = me.businesses.find((b) => b.businessId === selected.businessId)
  const today = new Intl.DateTimeFormat('en-AU', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())
  return (
    <>
      <PageHeader title={`Good ${greeting()}, ${me.user.fullName.split(' ')[0]}`} description={`${today} · ${business?.displayName} · ${roleLabel[selected.role]}`} />
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="min-w-0 lg:col-span-2">
          <Agenda />
        </div>
        <Card title={selected.locationIds === null ? 'All branches' : 'Your branches'} padded={false}>
          {locations.isPending && <div className="p-5"><Skeleton lines={2} /></div>}
          {locations.error && <div className="p-5"><Alert>{locations.error.message}</Alert></div>}
          {locations.data?.length === 0 && (
            <p className="px-5 py-6 text-sm text-muted">
              {selected.role === 'OWNER' ? 'No branches yet.' : 'You are not assigned to a branch yet.'}
            </p>
          )}
          <ul className="divide-y divide-line">
            {locations.data?.map((l) => (
              <li key={l.id} className="px-5 py-3.5">
                <p className="text-sm font-medium">{l.name}</p>
                <p className="flex items-center gap-1 text-[13px] text-muted">
                  <MapPin className="size-3.5" aria-hidden />
                  {l.suburb}, {l.state}
                </p>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </>
  )
}

function greeting() {
  const hour = new Date().getHours()
  return hour < 12 ? 'morning' : hour < 18 ? 'afternoon' : 'evening'
}
