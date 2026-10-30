import { describe, expect, it } from 'vitest'
import { addTimeRange, dayError } from './BranchDetailPage'

describe('addTimeRange', () => {
  it('splits a day that runs through lunch into a midday break', () => {
    expect(addTimeRange([{ opensAt: '09:00', closesAt: '17:00' }])).toEqual([
      { opensAt: '09:00', closesAt: '12:30' },
      { opensAt: '13:30', closesAt: '17:00' },
    ])
  })

  it('adds an evening range after a day that ends before lunch or after a break', () => {
    expect(addTimeRange([{ opensAt: '13:30', closesAt: '17:00' }])).toEqual([
      { opensAt: '13:30', closesAt: '17:00' },
      { opensAt: '18:00', closesAt: '20:00' },
    ])
  })

  it('never produces overlapping ranges', () => {
    expect(dayError(addTimeRange([{ opensAt: '09:00', closesAt: '17:00' }]))).toBeUndefined()
    expect(dayError(addTimeRange([{ opensAt: '09:00', closesAt: '12:00' }]))).toBeUndefined()
  })
})

describe('dayError', () => {
  it('accepts closed days and valid ranges', () => {
    expect(dayError([])).toBeUndefined()
    expect(dayError([{ opensAt: '09:00', closesAt: '12:30' }, { opensAt: '13:30', closesAt: '17:00' }])).toBeUndefined()
  })

  it('rejects backwards and overlapping ranges like the server does', () => {
    expect(dayError([{ opensAt: '18:00', closesAt: '09:00' }])).toMatch(/before/)
    expect(dayError([{ opensAt: '09:00', closesAt: '13:00' }, { opensAt: '12:00', closesAt: '17:00' }])).toMatch(/overlap/)
  })
})
