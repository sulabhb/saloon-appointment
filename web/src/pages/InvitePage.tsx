import { useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { useAcceptInvite, useInvite } from '../api/queries'
import { Alert, Button, Spinner, TextField, roleLabel } from '../components/ui'
import { CenteredPage } from './LoginPage'

const MIN_PASSWORD_LENGTH = 12

/** Landing page of an invite link (TEN-2): join the business with a new or existing account. */
export function InvitePage() {
  const { token = '' } = useParams()
  const invite = useInvite(token)

  if (invite.isPending) {
    return (
      <CenteredPage title="Invitation">
        <Spinner label="Checking your invite" />
      </CenteredPage>
    )
  }
  if (invite.error) {
    const gone = invite.error instanceof ApiError && invite.error.status === 410
    return (
      <CenteredPage title={gone ? 'This invite can no longer be used' : 'Invite not found'}>
        <p className="text-muted">
          {gone
            ? 'It has expired or was already used. Ask whoever invited you to send a new one.'
            : 'Check that you opened the full link from your invitation.'}
        </p>
      </CenteredPage>
    )
  }

  const { businessName, email, role, accountExists } = invite.data
  return (
    <CenteredPage
      title={`Join ${businessName}`}
      subtitle={`You have been invited as ${roleLabel[role].toLowerCase()} with ${email}.`}
    >
      <AcceptForm token={token} accountExists={accountExists} />
    </CenteredPage>
  )
}

function AcceptForm({ token, accountExists }: { token: string; accountExists: boolean }) {
  const accept = useAcceptInvite(token)
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [touched, setTouched] = useState(false)

  const nameOk = fullName.trim().length > 0
  const passwordOk = password.length >= MIN_PASSWORD_LENGTH
  const confirmOk = confirm === password
  const valid = accountExists ? password.length > 0 : nameOk && passwordOk && confirmOk

  // New accounts only; errors appear after the first submit attempt.
  const showErrors = !accountExists && touched
  const nameError = showErrors && !nameOk ? 'Enter your full name.' : undefined
  const passwordError = showErrors && !passwordOk
    ? `Use at least ${MIN_PASSWORD_LENGTH} characters. A short phrase is easier to remember than symbols.`
    : undefined
  const confirmError = showErrors && !confirmOk ? 'The passwords do not match.' : undefined

  function submit(event: FormEvent) {
    event.preventDefault()
    setTouched(true)
    if (!valid) return
    accept.mutate(
      accountExists ? { password } : { fullName: fullName.trim(), password },
      { onSuccess: () => navigate('/staff', { replace: true }) },
    )
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
      {accept.error && <Alert>{accept.error.message}</Alert>}
      {accountExists ? (
        <>
          <p className="text-sm text-muted">You already have an account. Confirm with your password to add this business to it.</p>
          <TextField
            label="Password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </>
      ) : (
        <>
          <TextField
            label="Full name"
            autoComplete="name"
            value={fullName}
            error={nameError}
            onChange={(e) => setFullName(e.target.value)}
          />
          <TextField
            label="Choose a password"
            type="password"
            autoComplete="new-password"
            value={password}
            error={passwordError}
            hint={`At least ${MIN_PASSWORD_LENGTH} characters.`}
            onChange={(e) => setPassword(e.target.value)}
          />
          <TextField
            label="Repeat password"
            type="password"
            autoComplete="new-password"
            value={confirm}
            error={confirmError}
            onChange={(e) => setConfirm(e.target.value)}
          />
        </>
      )}
      <Button type="submit" busy={accept.isPending}>
        {accountExists ? 'Join business' : 'Create account and join'}
      </Button>
    </form>
  )
}
