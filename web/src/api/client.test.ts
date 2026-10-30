import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api, readCsrfToken } from './client'

function respond(status: number, body?: unknown) {
  return vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('readCsrfToken', () => {
  it('reads and decodes the XSRF-TOKEN cookie', () => {
    expect(readCsrfToken('a=1; XSRF-TOKEN=abc%3D%3D; b=2')).toBe('abc==')
  })

  it('is undefined when the cookie is missing', () => {
    expect(readCsrfToken('a=1')).toBeUndefined()
  })
})

describe('api', () => {
  it('sends JSON and the CSRF header on changes', async () => {
    document.cookie = 'XSRF-TOKEN=token-123'
    const fetch = respond(200, { ok: true })
    vi.stubGlobal('fetch', fetch)

    await api('/auth/business', { method: 'POST', body: { businessId: 'b1' } })

    const [url, init] = fetch.mock.calls[0]!
    expect(url).toBe('/api/v1/auth/business')
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'token-123' })
    expect(init.body).toBe('{"businessId":"b1"}')
  })

  it('does not send the CSRF header on reads', async () => {
    document.cookie = 'XSRF-TOKEN=token-123'
    const fetch = respond(200, [])
    vi.stubGlobal('fetch', fetch)

    await api('/staff/locations')

    expect(fetch.mock.calls[0]![1].headers).not.toHaveProperty('X-XSRF-TOKEN')
  })

  it('turns error bodies into ApiError with the server code and message', async () => {
    vi.stubGlobal('fetch', respond(409, { error: { code: 'SLUG_TAKEN', message: 'That salon code is already in use.' } }))

    await expect(api('/platform/businesses', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 409,
      code: 'SLUG_TAKEN',
      message: 'That salon code is already in use.',
    })
  })

  it('reports network failures as NETWORK_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const error = await api('/auth/me').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('NETWORK_ERROR')
  })

  it('returns nothing for 204 responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(api('/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })
})
