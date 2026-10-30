import { describe, expect, it } from 'vitest'
import { formatDuration, formatMoney, readableTextOn } from './ui'

describe('formatMoney', () => {
  it('drops cents for whole dollars and keeps them otherwise', () => {
    expect(formatMoney(8500)).toBe('$85')
    expect(formatMoney(8550)).toBe('$85.50')
    expect(formatMoney(0)).toBe('$0')
  })
})

describe('formatDuration', () => {
  it('reads naturally for minutes and hours', () => {
    expect(formatDuration(45)).toBe('45 min')
    expect(formatDuration(60)).toBe('1 h')
    expect(formatDuration(90)).toBe('1 h 30 min')
  })
})

describe('readableTextOn', () => {
  it('uses white on dark brand colours and dark text on light ones (WCAG AA)', () => {
    expect(readableTextOn('#4f6f5c')).toBe('#ffffff')
    expect(readableTextOn('#7a4e8c')).toBe('#ffffff')
    expect(readableTextOn('#f4d35e')).toBe('#1a1a1a')
    expect(readableTextOn('#ffffff')).toBe('#1a1a1a')
  })
})
