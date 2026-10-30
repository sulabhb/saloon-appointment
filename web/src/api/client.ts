/** An error response from the API: {"error": {"code", "message"}}. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

/** Spring Security writes the CSRF token to a readable cookie; changes must echo it in a header. */
export function readCsrfToken(cookies: string = document.cookie): string | undefined {
  const entry = cookies.split('; ').find((c) => c.startsWith(`${CSRF_COOKIE}=`))
  return entry ? decodeURIComponent(entry.slice(CSRF_COOKIE.length + 1)) : undefined
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

/**
 * Calls the backend API. The web console signs in with the session cookie, so requests stay
 * same-origin (the Vite dev server proxies /api to the backend).
 */
export async function api<T>(path: string, options: { method?: Method; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    const token = readCsrfToken()
    if (token) headers[CSRF_HEADER] = token
  }

  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'same-origin',
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Could not reach the server. Check your connection and try again.')
  }

  if (response.status === 204) {
    return undefined as T
  }
  const data: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const error = (data as { error?: { code?: string; message?: string } } | null)?.error
    throw new ApiError(response.status, error?.code ?? 'UNKNOWN', error?.message ?? 'Something went wrong. Please try again.')
  }
  return data as T
}
