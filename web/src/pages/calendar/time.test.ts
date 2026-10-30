import { describe, expect, it } from 'vitest'
import { hhmm, hourLabel, localMinutes, minutesOf, zonedInstant } from './time'

describe('calendar time helpers', () => {
  it('converts between clock times and minutes', () => {
    expect(minutesOf('09:30:00')).toBe(570)
    expect(hhmm(570)).toBe('09:30')
    expect(hourLabel(0)).toBe('12 am')
    expect(hourLabel(13 * 60)).toBe('1 pm')
  })

  it('reads an instant in the branch time zone', () => {
    expect(localMinutes('2026-11-02T23:00:00Z', 'Australia/Sydney')).toBe(10 * 60)
    expect(localMinutes('2026-11-02T23:00:00Z', 'Australia/Brisbane')).toBe(9 * 60)
  })

  it('turns a branch wall-clock time into an instant, across daylight saving', () => {
    expect(zonedInstant('2026-11-03', 10 * 60, 'Australia/Sydney')).toBe('2026-11-02T23:00:00.000Z')
    expect(zonedInstant('2026-11-03', 10 * 60, 'Australia/Brisbane')).toBe('2026-11-03T00:00:00.000Z')
    // Sydney in winter (no daylight saving): UTC+10.
    expect(zonedInstant('2026-06-01', 9 * 60 + 15, 'Australia/Sydney')).toBe('2026-05-31T23:15:00.000Z')
  })
})
