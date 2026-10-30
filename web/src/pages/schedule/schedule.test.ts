import { describe, expect, it } from 'vitest'
import { addDays, addShift, intervalsError, longDate, mondayOf, shortTime, timeRange, weekdayOf, weekLabel } from './schedule'

describe('dates', () => {
  it('finds the Monday of any day in the week', () => {
    expect(mondayOf('2030-06-03')).toBe('2030-06-03')
    expect(mondayOf('2030-06-09')).toBe('2030-06-03')
    expect(mondayOf('2030-06-10')).toBe('2030-06-10')
    expect(weekdayOf('2030-06-09')).toBe(7)
  })

  it('crosses months and years', () => {
    expect(addDays('2030-12-29', 7)).toBe('2031-01-05')
    expect(weekLabel('2030-06-03')).toBe('3 – 9 June 2030')
    expect(weekLabel('2030-07-29')).toBe('29 July – 4 August 2030')
    expect(longDate('2030-06-03')).toBe('Monday 3 June')
  })
})

describe('times', () => {
  it('formats compact rota times', () => {
    expect(shortTime('09:00:00')).toBe('9am')
    expect(shortTime('12:30')).toBe('12:30pm')
    expect(shortTime('00:15')).toBe('12:15am')
    expect(timeRange('09:00:00', '17:00:00')).toBe('9am–5pm')
  })
})

describe('intervalsError', () => {
  const at = (locationId: string, startsAt: string, endsAt: string) => ({ locationId, startsAt, endsAt })

  it('allows back-to-back shifts at different branches', () => {
    expect(intervalsError([at('a', '09:00', '12:30'), at('b', '12:30:00', '17:00')])).toBeUndefined()
  })

  it('rejects overlaps and backwards times like the server', () => {
    expect(intervalsError([at('a', '09:00', '13:00'), at('b', '12:00', '17:00')])).toMatch(/two branches/)
    expect(intervalsError([at('a', '09:00', '13:00'), at('a', '12:00', '17:00')])).toMatch(/overlap/)
    expect(intervalsError([at('a', '17:00', '09:00')])).toMatch(/before/)
  })
})

describe('addShift', () => {
  it('splits a full day into a lunch break and never overlaps', () => {
    const day = [{ locationId: 'a', startsAt: '09:00:00', endsAt: '17:00:00' }]
    const split = addShift(day, day[0]!)
    expect(split.map((s) => [s.startsAt, s.endsAt])).toEqual([
      ['09:00', '12:30'],
      ['13:30', '17:00'],
    ])
    expect(intervalsError(addShift(split, split[0]!))).toBeUndefined()
  })
})
