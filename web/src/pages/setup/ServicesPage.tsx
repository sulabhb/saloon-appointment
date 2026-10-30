import { Clock3, FolderPlus, Pencil, Plus, Scissors, Store, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import {
  useBranches,
  useCatalog,
  useCreateCategory,
  useCreateService,
  useRenameCategory,
  useUpdateService,
} from '../../api/adminQueries'
import type { CategoryView, ServiceView } from '../../api/adminTypes'
import { useMe } from '../../api/queries'
import { Drawer } from '../../components/Drawer'
import { useToast } from '../../components/Toast'
import {
  Alert,
  Badge,
  Button,
  Card,
  CheckboxList,
  EmptyState,
  PageHeader,
  SelectField,
  Skeleton,
  TextAreaField,
  TextField,
  Toggle,
  formatDuration,
  formatMoney,
} from '../../components/ui'

type Editing = { kind: 'new'; categoryId?: string } | { kind: 'edit'; service: ServiceView } | null

/** The service menu, grouped by category (SVC-1, SVC-2). */
export function ServicesPage() {
  const catalog = useCatalog()
  const isOwner = useMe().data?.selected?.role === 'OWNER'
  const [editing, setEditing] = useState<Editing>(null)
  const [addingCategory, setAddingCategory] = useState(false)

  const categories = catalog.data ?? []
  return (
    <>
      <PageHeader
        title="Services"
        description="Your menu. Each branch chooses which services it offers."
        actions={
          isOwner && (
            <>
              <Button variant="secondary" icon={FolderPlus} onClick={() => setAddingCategory(true)}>
                New category
              </Button>
              <Button icon={Plus} disabled={categories.length === 0} onClick={() => setEditing({ kind: 'new' })}>
                New service
              </Button>
            </>
          )
        }
      />
      {catalog.isPending && <Skeleton />}
      {catalog.error && <Alert>{catalog.error.message}</Alert>}
      {catalog.data?.length === 0 && (
        <EmptyState
          icon={Scissors}
          title="Start with a category"
          action={isOwner && <Button icon={FolderPlus} onClick={() => setAddingCategory(true)}>New category</Button>}
        >
          Group services the way customers think about them, such as Cuts, Colour or Treatments.
        </EmptyState>
      )}
      <div className="flex flex-col gap-6">
        {categories.map((category) => (
          <CategoryCard
            key={category.id}
            category={category}
            isOwner={isOwner}
            onAdd={() => setEditing({ kind: 'new', categoryId: category.id })}
            onEdit={(service) => setEditing({ kind: 'edit', service })}
          />
        ))}
      </div>
      <CategoryDrawer open={addingCategory} onClose={() => setAddingCategory(false)} />
      {editing && (
        <ServiceDrawer
          key={editing.kind === 'edit' ? editing.service.id : `new-${editing.categoryId}`}
          editing={editing}
          categories={categories}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  )
}

function CategoryCard({
  category,
  isOwner,
  onAdd,
  onEdit,
}: {
  category: CategoryView
  isOwner: boolean
  onAdd: () => void
  onEdit: (service: ServiceView) => void
}) {
  const rename = useRenameCategory()
  const [renaming, setRenaming] = useState(false)
  const [name, setName] = useState(category.name)

  const title = renaming ? (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        rename.mutate({ id: category.id, name }, { onSuccess: () => setRenaming(false) })
      }}
      className="flex items-center gap-2"
    >
      <input
        aria-label="Category name"
        autoFocus
        value={name}
        onChange={(e) => setName(e.target.value)}
        className="h-8 rounded-lg border border-line-strong bg-surface px-2.5 text-[15px] font-semibold"
      />
      <Button size="sm" type="submit" busy={rename.isPending}>
        Save
      </Button>
      <Button size="sm" variant="ghost" onClick={() => setRenaming(false)}>
        Cancel
      </Button>
    </form>
  ) : (
    <span className="inline-flex items-center gap-2">
      {category.name}
      <span className="text-[13px] font-normal text-muted">{category.services.length}</span>
      {isOwner && (
        <button type="button" aria-label={`Rename ${category.name}`} onClick={() => setRenaming(true)} className="rounded p-1 text-subtle hover:bg-sunken hover:text-ink">
          <Pencil className="size-3.5" aria-hidden />
        </button>
      )}
    </span>
  )

  return (
    <Card
      title={title}
      actions={isOwner && <Button variant="ghost" size="sm" icon={Plus} onClick={onAdd}>Add service</Button>}
      padded={false}
    >
      {category.services.length === 0 ? (
        <p className="px-5 py-6 text-sm text-muted">No services in this category yet.</p>
      ) : (
        <ul className="divide-y divide-line">
          {category.services.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                disabled={!isOwner}
                onClick={() => onEdit(s)}
                className="flex w-full flex-wrap items-center gap-x-6 gap-y-2 px-5 py-4 text-left transition-colors enabled:hover:bg-sunken/50"
              >
                <div className="min-w-0 flex-1 basis-60">
                  <p className={`text-sm font-medium ${s.active ? '' : 'text-muted'}`}>
                    {s.name}
                    {!s.active && <span className="ml-2"><Badge>Inactive</Badge></span>}
                  </p>
                  {s.description && <p className="truncate text-[13px] text-muted">{s.description}</p>}
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <Badge icon={Clock3}>{formatDuration(s.durationMin)}</Badge>
                  <Badge icon={Store}>{s.branchCount} {s.branchCount === 1 ? 'branch' : 'branches'}</Badge>
                  <Badge icon={Users} tone={s.staffCount === 0 && s.active ? 'warning' : 'neutral'}>
                    {s.staffCount} staff
                  </Badge>
                </div>
                <p className="w-24 text-right text-sm font-medium tabular-nums">
                  {s.priceIsFrom && <span className="font-normal text-muted">from </span>}
                  {formatMoney(s.priceCents)}
                </p>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function CategoryDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const create = useCreateCategory()
  const toast = useToast()
  const [name, setName] = useState('')
  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="New category"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="new-category" busy={create.isPending} disabled={!name.trim()}>
            Create category
          </Button>
        </>
      }
    >
      <form
        id="new-category"
        onSubmit={(e) => {
          e.preventDefault()
          create.mutate(name.trim(), {
            onSuccess: () => {
              toast('Category created')
              setName('')
              onClose()
            },
          })
        }}
      >
        {create.error && <div className="mb-4"><Alert>{create.error.message}</Alert></div>}
        <TextField label="Name" placeholder="Colour" value={name} onChange={(e) => setName(e.target.value)} />
      </form>
    </Drawer>
  )
}

const DURATIONS = [15, 30, 45, 60, 75, 90, 120, 150, 180, 240]

function ServiceDrawer({
  editing,
  categories,
  onClose,
}: {
  editing: Exclude<Editing, null>
  categories: CategoryView[]
  onClose: () => void
}) {
  const existing = editing.kind === 'edit' ? editing.service : undefined
  const create = useCreateService()
  const update = useUpdateService()
  const branches = useBranches()
  const toast = useToast()
  const mutation = existing ? update : create

  const [categoryId, setCategoryId] = useState(
    existing?.categoryId ?? (editing.kind === 'new' ? editing.categoryId : undefined) ?? categories[0]?.id ?? '',
  )
  const [name, setName] = useState(existing?.name ?? '')
  const [description, setDescription] = useState(existing?.description ?? '')
  const [durationMin, setDurationMin] = useState(existing?.durationMin ?? 45)
  const [bufferAfterMin, setBufferAfterMin] = useState(existing?.bufferAfterMin ?? 0)
  const [price, setPrice] = useState(existing ? String(existing.priceCents / 100) : '')
  const [priceIsFrom, setPriceIsFrom] = useState(existing?.priceIsFrom ?? false)
  const [customerNotice, setCustomerNotice] = useState(existing?.customerNotice ?? '')
  const [active, setActive] = useState(existing?.active ?? true)
  const activeBranches = (branches.data ?? []).filter((b) => b.active)
  const [offerAt, setOfferAt] = useState<string[] | null>(null)
  const selectedBranches = offerAt ?? activeBranches.map((b) => b.id)

  const priceCents = Math.round(Number.parseFloat(price || 'NaN') * 100)
  const valid = name.trim() && categoryId && Number.isFinite(priceCents) && priceCents >= 0

  function submit(event: FormEvent) {
    event.preventDefault()
    const input = {
      categoryId,
      name: name.trim(),
      description: description.trim() || undefined,
      durationMin,
      bufferAfterMin,
      priceCents,
      priceIsFrom,
      customerNotice: customerNotice.trim() || undefined,
      active,
    }
    const done = () => {
      toast(existing ? 'Service saved' : 'Service created')
      onClose()
    }
    if (existing) update.mutate({ ...input, id: existing.id }, { onSuccess: done })
    else create.mutate({ ...input, offerAtLocationIds: selectedBranches }, { onSuccess: done })
  }

  return (
    <Drawer
      open
      onClose={onClose}
      title={existing ? existing.name : 'New service'}
      description={existing ? 'Changes apply to new bookings only.' : 'Customers see the name, duration and price.'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" form="service-form" busy={mutation.isPending} disabled={!valid}>
            {existing ? 'Save service' : 'Create service'}
          </Button>
        </>
      }
    >
      <form id="service-form" onSubmit={submit} className="flex flex-col gap-4">
        {mutation.error && <Alert>{mutation.error.message}</Alert>}
        <TextField label="Name" placeholder="Cut and blow-dry" value={name} onChange={(e) => setName(e.target.value)} />
        <SelectField label="Category" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </SelectField>
        <TextAreaField label="Description" optional maxLength={1000} value={description} onChange={(e) => setDescription(e.target.value)} />
        <div className="grid grid-cols-2 gap-4">
          <SelectField label="Duration" value={durationMin} onChange={(e) => setDurationMin(Number(e.target.value))}>
            {[...new Set([...DURATIONS, durationMin])].sort((a, b) => a - b).map((d) => (
              <option key={d} value={d}>{formatDuration(d)}</option>
            ))}
          </SelectField>
          <SelectField
            label="Clean-up time"
            hint="Blocks the stylist after the service."
            value={bufferAfterMin}
            onChange={(e) => setBufferAfterMin(Number(e.target.value))}
          >
            {[0, 5, 10, 15, 20, 30].map((d) => (
              <option key={d} value={d}>{d === 0 ? 'None' : `${d} min`}</option>
            ))}
          </SelectField>
        </div>
        <TextField label="Price" prefix="$" inputMode="decimal" placeholder="85" value={price} onChange={(e) => setPrice(e.target.value)} hint="Including GST." />
        <Toggle label='Show as "from" price' description="For services where the final price depends on hair length or similar." checked={priceIsFrom} onChange={setPriceIsFrom} />
        <TextField label="Note shown before booking" optional placeholder="A patch test is required 48 hours before" value={customerNotice} onChange={(e) => setCustomerNotice(e.target.value)} />
        {existing ? (
          <Toggle label="Available to book" description="Inactive services are hidden from customers." checked={active} onChange={setActive} />
        ) : (
          <CheckboxList
            legend="Offer at branches"
            items={activeBranches.map((b) => ({ id: b.id, label: b.name, detail: b.suburb }))}
            selected={selectedBranches}
            onChange={setOfferAt}
            empty="Add a branch first; you can offer this service there later."
          />
        )}
      </form>
    </Drawer>
  )
}
