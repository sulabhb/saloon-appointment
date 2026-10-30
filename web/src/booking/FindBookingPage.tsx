import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../api/client'
import { usePublicBusiness } from './api'
import { BookingFrame, Notice, SalonHeader } from './BookingFrame'

const inputStyle =
  'h-12 w-full rounded-xl border border-[#CFCBC6] bg-white px-4 text-base outline-none focus:border-(--brand) focus:ring-2 focus:ring-(--brand)/25'

/** Lost the link from the email (MGT-6): reference and email bring a new one to the address on file. */
export function FindBookingPage() {
  const { business: slug = '' } = useParams()
  const business = usePublicBusiness(slug)
  const [reference, setReference] = useState('')
  const [email, setEmail] = useState('')
  const recover = useMutation({
    mutationFn: () =>
      api<{ message: string }>(`/public/b/${encodeURIComponent(slug)}/manage/recover`, {
        method: 'POST',
        body: { reference: reference.trim(), email: email.trim() },
      }),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    recover.mutate()
  }

  return (
    <BookingFrame accentColor={business.data?.accentColor ?? null}>
      <SalonHeader name={business.data?.displayName ?? ''} />
      <main className="mx-auto max-w-[34rem] px-5 pb-16">
        <h1 className="text-[1.375rem] font-semibold">Find your booking</h1>
        <p className="mt-1 mb-6 text-pretty text-[#6E6A65]">
          Enter the reference from your confirmation email and the email you booked with. We'll send a new link to that address;
          older links stop working.
        </p>
        {recover.isSuccess ? (
          <Notice>{recover.data.message}</Notice>
        ) : (
          <form onSubmit={submit} className="flex flex-col gap-5">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="reference" className="font-semibold">
                Booking reference
              </label>
              <input
                id="reference"
                autoCapitalize="characters"
                autoComplete="off"
                maxLength={20}
                placeholder="For example C6B5QJMC"
                value={reference}
                onChange={(e) => setReference(e.target.value)}
                className={`${inputStyle} tracking-normal uppercase tabular-nums`}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="email" className="font-semibold">
                Email
              </label>
              <input id="email" type="email" autoComplete="email" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} className={inputStyle} />
            </div>
            {recover.error && <Notice tone="error">{recover.error.message}</Notice>}
            <button
              type="submit"
              disabled={!reference.trim() || !email.includes('@') || recover.isPending}
              className="h-12 rounded-full bg-(--brand) px-6 font-semibold text-(--on-brand) transition-transform duration-150 ease-out active:scale-[0.97] disabled:opacity-50"
            >
              {recover.isPending ? 'Sending…' : 'Email me a new link'}
            </button>
          </form>
        )}
        <p className="mt-8">
          <Link to={`/book/${slug}`} className="font-medium underline underline-offset-4">
            Make a new booking
          </Link>
        </p>
      </main>
    </BookingFrame>
  )
}
