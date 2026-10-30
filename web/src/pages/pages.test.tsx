import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { InvitePage } from './InvitePage'
import { safeNextPath } from './LoginPage'
import { suggestSlug } from './platform/NewBusinessPage'

describe('safeNextPath', () => {
  it('keeps same-site paths', () => {
    expect(safeNextPath('/platform/businesses/1')).toBe('/platform/businesses/1')
  })

  it('rejects other sites and missing values', () => {
    expect(safeNextPath('//evil.example')).toBe('/')
    expect(safeNextPath('https://evil.example')).toBe('/')
    expect(safeNextPath(null)).toBe('/')
  })
})

describe('suggestSlug', () => {
  it('turns a salon name into a salon code', () => {
    expect(suggestSlug('Bloom Studio')).toBe('bloom-studio')
    expect(suggestSlug("  Maya's Hair & Beauty! ")).toBe('maya-s-hair-beauty')
  })
})

describe('InvitePage', () => {
  afterEach(() => vi.unstubAllGlobals())

  function renderInvite(status: number, body: unknown) {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })))
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/invite/abc']}>
          <Routes>
            <Route path="/invite/:token" element={<InvitePage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )
  }

  it('asks new people for a name and password', async () => {
    renderInvite(200, {
      businessName: 'Bloom Studio',
      email: 'maya@bloom.test',
      role: 'OWNER',
      accountExists: false,
      expiresAt: '2026-11-04T00:00:00Z',
    })

    expect(await screen.findByRole('heading', { name: 'Join Bloom Studio' })).toBeInTheDocument()
    expect(screen.getByLabelText('Full name')).toBeInTheDocument()
    expect(screen.getByLabelText('Choose a password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create account and join' })).toBeInTheDocument()
  })

  it('asks existing accounts only for their password', async () => {
    renderInvite(200, {
      businessName: 'Bloom Studio',
      email: 'alex@example.test',
      role: 'MANAGER',
      accountExists: true,
      expiresAt: '2026-11-04T00:00:00Z',
    })

    expect(await screen.findByRole('button', { name: 'Join business' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Full name')).not.toBeInTheDocument()
  })

  it('explains used or expired invites', async () => {
    renderInvite(410, { error: { code: 'INVITE_EXPIRED', message: 'gone' } })

    expect(await screen.findByRole('heading', { name: 'This invite can no longer be used' })).toBeInTheDocument()
  })
})
