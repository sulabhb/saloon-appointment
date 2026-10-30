import { Scissors } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { useMe, useSignIn } from '../api/queries'
import { Alert, Button, Card, TextField } from '../components/ui'

/** Only same-site paths, so a crafted link cannot send people elsewhere after signing in. */
export function safeNextPath(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}

export function LoginPage() {
  const [params] = useSearchParams()
  const next = safeNextPath(params.get('next'))
  const me = useMe()
  const signIn = useSignIn()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  if (me.data) {
    return <Navigate to={next} replace />
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    signIn.mutate({ email, password }, { onSuccess: () => navigate(next, { replace: true }) })
  }

  return (
    <CenteredPage title="Sign in" subtitle="Salon Console for staff, owners and the platform team.">
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        {signIn.error && <Alert>{messageFor(signIn.error)}</Alert>}
        <TextField
          label="Email"
          type="email"
          autoComplete="username"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Button type="submit" busy={signIn.isPending} disabled={!email || !password}>
          Sign in
        </Button>
        <Link to="/forgot-password" className="text-center text-sm text-muted hover:text-ink hover:underline">
          Forgot your password?
        </Link>
      </form>
    </CenteredPage>
  )
}

function messageFor(error: Error) {
  if (error instanceof ApiError && error.code === 'INVALID_CREDENTIALS') {
    return 'Email or password is incorrect. After five wrong attempts the account is locked for 15 minutes.'
  }
  return error.message
}

export function CenteredPage({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <main className="grid min-h-dvh place-items-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center gap-3">
          <span className="flex size-10 items-center justify-center rounded-xl bg-accent text-on-accent shadow-card">
            <Scissors className="size-5" aria-hidden />
          </span>
          <span className="font-semibold">Salon Console</span>
        </div>
        <Card>
          <h1 className="text-xl font-semibold">{title}</h1>
          {subtitle ? <p className="mt-1 mb-6 text-sm text-muted">{subtitle}</p> : <div className="mb-4" />}
          {children}
        </Card>
      </div>
    </main>
  )
}
