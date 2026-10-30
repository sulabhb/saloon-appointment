import { describe, expect, it } from 'vitest'
import { clock, cutoffInWords, fullDate, googleCalendarUrl, icsDataUrl } from './format'

describe('booking formatting', () => {
  it('shows times in the branch time zone, not the browser one', () => {
    // 23:00 UTC on 2 Nov is 10:00 on 3 Nov in Sydney (daylight saving) and 9:00 in Brisbane.
    expect(clock('2026-11-02T23:00:00Z', 'Australia/Sydney')).toBe('10:00 am')
    expect(clock('2026-11-02T23:00:00Z', 'Australia/Brisbane')).toBe('9:00 am')
    expect(fullDate('2026-11-02T23:00:00Z', 'Australia/Sydney')).toBe('Tuesday 3 November')
  })

  it('describes the cancellation cut-off in words', () => {
    expect(cutoffInWords(1440)).toBe('24 hours')
    expect(cutoffInWords(60)).toBe('1 hour')
    expect(cutoffInWords(2880)).toBe('2 days')
    expect(cutoffInWords(90)).toBe('90 minutes')
  })

  it('builds calendar entries in UTC', () => {
    const event = { title: 'Cut, Luna Hair', start: '2026-11-02T23:00:00Z', end: '2026-11-03T00:00:00Z', location: 'A; B', details: 'Ref X' }
    expect(googleCalendarUrl(event)).toContain('dates=20261102T230000Z%2F20261103T000000Z')
    expect(decodeURIComponent(icsDataUrl(event, 'X@salon'))).toContain('LOCATION:A' + String.fromCharCode(92) + '; B')
  })
})

describe('branchLabel', () => {
  it('avoids repeating a suburb the branch is named after', async () => {
    const { branchLabel } = await import('./format')
    expect(branchLabel({ name: 'Fortitude Valley', suburb: 'Fortitude Valley', state: 'QLD' })).toBe('Fortitude Valley, QLD')
    expect(branchLabel({ name: 'Sydney CBD', suburb: 'Sydney', state: 'NSW' })).toBe('Sydney CBD, Sydney')
  })
})
