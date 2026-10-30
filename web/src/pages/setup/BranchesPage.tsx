import { CalendarClock, ChevronRight, MapPin, Plus, Scissors, Store, Users } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useBranches, useCreateBranch } from '../../api/adminQueries'
import { ApiError } from '../../api/client'
import { useMe } from '../../api/queries'
import { Drawer } from '../../components/Drawer'
import { useToast } from '../../components/Toast'
import { Alert, Badge, Button, EmptyState, PageHeader, Skeleton } from '../../components/ui'
import { BranchForm } from './BranchForm'

export function BranchesPage() {
  const branches = useBranches()
  const isOwner = useMe().data?.selected?.role === 'OWNER'
  const [adding, setAdding] = useState(false)

  return (
    <>
      <PageHeader
        title="Branches"
        description="Locations customers can book at, with their hours and services."
        actions={isOwner && <Button icon={Plus} onClick={() => setAdding(true)}>Add branch</Button>}
      />
      {branches.isPending && <Skeleton />}
      {branches.error && <Alert>{branches.error.message}</Alert>}
      {branches.data?.length === 0 && (
        <EmptyState
          icon={Store}
          title="No branches yet"
          action={isOwner && <Button icon={Plus} onClick={() => setAdding(true)}>Add your first branch</Button>}
        >
          Add the salon customers come to. You can add more locations at any time.
        </EmptyState>
      )}
      {branches.data && branches.data.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {branches.data.map((b) => (
            <li key={b.id}>
              <Link
                to={`/branches/${b.id}`}
                className="group flex h-full flex-col rounded-xl border border-line bg-surface p-5 shadow-card transition-colors hover:border-line-strong"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate font-semibold">{b.name}</p>
                    <p className="mt-0.5 flex items-center gap-1 text-[13px] text-muted">
                      <MapPin className="size-3.5" aria-hidden />
                      {b.suburb}, {b.state}
                    </p>
                  </div>
                  {!b.active && <Badge>Inactive</Badge>}
                </div>
                <div className="mt-5 flex flex-wrap gap-2">
                  <Badge tone={b.openDays > 0 ? 'neutral' : 'warning'} icon={CalendarClock}>
                    {b.openDays > 0 ? `Open ${b.openDays} ${b.openDays === 1 ? 'day' : 'days'}` : 'No hours yet'}
                  </Badge>
                  <Badge icon={Scissors}>{b.servicesOffered} {b.servicesOffered === 1 ? 'service' : 'services'}</Badge>
                  <Badge icon={Users}>{b.staffCount} staff</Badge>
                </div>
                <span className="mt-auto flex items-center gap-1 pt-5 text-[13px] font-medium text-accent">
                  Manage
                  <ChevronRight className="size-3.5 transition-transform group-hover:translate-x-0.5" aria-hidden />
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <AddBranchDrawer open={adding} onClose={() => setAdding(false)} />
    </>
  )
}

function AddBranchDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const create = useCreateBranch()
  const navigate = useNavigate()
  const toast = useToast()
  const slugTaken = create.error instanceof ApiError && create.error.code === 'SLUG_TAKEN'

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Add branch"
      description="You can set opening hours and services next."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="add-branch" busy={create.isPending}>
            Add branch
          </Button>
        </>
      }
    >
      {create.error && !slugTaken && (
        <div className="mb-4">
          <Alert>{create.error.message}</Alert>
        </div>
      )}
      <BranchForm
        id="add-branch"
        slugError={slugTaken ? create.error?.message : undefined}
        onSubmit={(input) =>
          create.mutate(input, {
            onSuccess: ({ id }) => {
              toast('Branch added')
              onClose()
              navigate(`/branches/${id}`)
            },
          })
        }
      />
    </Drawer>
  )
}
