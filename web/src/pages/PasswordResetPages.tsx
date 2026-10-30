import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../api/client'
import { Alert, Button, TextField } from '../components/ui'
import { CenteredPage } from './LoginPage'

const MIN_PASSWORD = 12

/** Asks for a reset link (DSH-1). The answer is the same whether or not the email has an account. */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const request = useMutation({
    mutationFn: () => api<{ message: string }>('/auth/password-reset', { method: 'POST', body: { email: email.trim() } }),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    request.mutate()
  }

  return (
    <CenteredPage title="Reset your password" subtitle="We'll email you a link to choose a new one.">
      {request.isSuccess ? (
        <div className="flex flex-col gap-4">
          <Alert tone="success">{request.data.message} The link works once, for an hour.</Alert>
          <Link to="/login" className="text-center text-sm text-muted hover:text-ink hover:underline">
            Back to sign in
          </Link>
        </div>
      ) : (
        <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
          {request.error && <Alert>{request.error.message}</Alert>}
          <TextField label="Email" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
          <Button type="submit" busy={request.isPending} disabled={!email.includes('@')}>
            Email me a link
          </Button>
          <Link to="/login" className="text-center text-sm text-muted hover:text-ink hover:underline">
            Back to sign in
          </Link>
        </form>
      )}
    </CenteredPage>
  )
}

/** Chooses the new password from the emailed link; signs out every device. */
export function ResetPasswordPage() {
  const { token = '' } = useParams()
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const reset = useMutation({
    mutationFn: () => api<void>('/auth/password-reset/confirm', { method: 'POST', body: { token, password } }),
  })
  const tooShort = password.length > 0 && password.length < MIN_PASSWORD
  const mismatch = repeat.length > 0 && repeat !== password

  function submit(event: FormEvent) {
    event.preventDefault()
    if (password.length >= MIN_PASSWORD && password === repeat) reset.mutate()
  }

  return (
    <CenteredPage title="Choose a new password" subtitle="You'll be signed out on every device, then sign in again.">
      {reset.isSuccess ? (
        <div className="flex flex-col gap-4">
          <Alert tone="success">Your password is changed.</Alert>
          <Link
            to="/login"
            className="flex h-10 items-center justify-center rounded-lg bg-accent text-sm font-medium text-on-accent shadow-card hover:bg-accent-strong"
          >
            Sign in
          </Link>
        </div>
      ) : (
        <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
          {reset.error && (
            <Alert>
              {reset.error.message}{' '}
              <Link to="/forgot-password" className="font-medium underline">
                Ask for a new link
              </Link>
            </Alert>
          )}
          <TextField
            label="New password"
            type="password"
            autoComplete="new-password"
            hint={`At least ${MIN_PASSWORD} characters. A short phrase is easy to remember.`}
            error={tooShort ? `Use at least ${MIN_PASSWORD} characters.` : undefined}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <TextField
            label="Repeat it"
            type="password"
            autoComplete="new-password"
            error={mismatch ? "The two passwords don't match." : undefined}
            value={repeat}
            onChange={(e) => setRepeat(e.target.value)}
          />
          <Button type="submit" busy={reset.isPending} disabled={password.length < MIN_PASSWORD || password !== repeat}>
            Save new password
          </Button>
        </form>
      )}
    </CenteredPage>
  )
}
