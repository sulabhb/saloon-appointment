import { Link } from 'react-router'
import { CenteredPage } from './LoginPage'

export function NotFoundPage() {
  return (
    <CenteredPage title="Page not found">
      <p className="text-muted">
        This page does not exist.{' '}
        <Link to="/" className="font-medium text-accent underline">
          Go to the start page
        </Link>
      </p>
    </CenteredPage>
  )
}
