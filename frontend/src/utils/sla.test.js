import { describe, expect, it } from 'vitest'
import { currentClock, describeClock, formatDuration, liveState } from './sla'

const START = '2026-01-05T09:00:00Z'
const DUE = '2026-01-05T13:00:00Z' // 4h window
const at = (time) => Date.parse(time)

describe('liveState', () => {
  const running = { dueAt: DUE, completedAt: null, state: 'ON_TRACK' }

  it('is on track until only a quarter of the window is left', () => {
    expect(liveState(running, START, at('2026-01-05T09:00:00Z'))).toBe('ON_TRACK')
    expect(liveState(running, START, at('2026-01-05T11:59:00Z'))).toBe('ON_TRACK')
    expect(liveState(running, START, at('2026-01-05T12:00:00Z'))).toBe('AT_RISK')
  })

  it('is breached from the deadline on', () => {
    expect(liveState(running, START, at(DUE))).toBe('BREACHED')
    expect(liveState(running, START, at('2026-01-09T00:00:00Z'))).toBe('BREACHED')
  })

  it('keeps the server state once the deadline is done', () => {
    const met = { dueAt: DUE, completedAt: '2026-01-05T10:00:00Z', state: 'MET' }
    expect(liveState(met, START, at('2026-02-01T00:00:00Z'))).toBe('MET')
  })

  it('stays paused while on hold, whatever the time', () => {
    const paused = { dueAt: DUE, completedAt: null, state: 'PAUSED' }
    expect(liveState(paused, START, at('2026-01-09T00:00:00Z'))).toBe('PAUSED')
    expect(describeClock(paused, 'PAUSED', at('2026-01-09T00:00:00Z'))).toBe('Paused')
  })

  it('handles a missing clock', () => {
    expect(liveState(null, START, at(DUE))).toBeNull()
  })
})

describe('formatDuration', () => {
  it.each([
    [30 * 1000, 'under 1m'],
    [45 * 60 * 1000, '45m'],
    [2 * 3600 * 1000, '2h'],
    [(2 * 60 + 15) * 60 * 1000, '2h 15m'],
    [3 * 86400 * 1000, '3d'],
    [(3 * 24 + 4) * 3600 * 1000, '3d 4h'],
    [-90 * 60 * 1000, '1h 30m'],
  ])('%i ms is "%s"', (ms, text) => {
    expect(formatDuration(ms)).toBe(text)
  })
})

describe('describeClock', () => {
  it('shows time left, overdue, met and missed', () => {
    const running = { dueAt: DUE, completedAt: null }
    expect(describeClock(running, 'ON_TRACK', at('2026-01-05T11:00:00Z'))).toBe('2h left')
    expect(describeClock(running, 'BREACHED', at('2026-01-05T16:00:00Z'))).toBe('Overdue 3h')

    const onTime = { dueAt: DUE, completedAt: '2026-01-05T12:00:00Z' }
    expect(describeClock(onTime, 'MET', at('2026-01-06T00:00:00Z'))).toBe('Met')

    const late = { dueAt: DUE, completedAt: '2026-01-05T13:05:00Z' }
    expect(describeClock(late, 'BREACHED', at('2026-01-06T00:00:00Z'))).toBe('Missed by 5m')
  })
})

describe('currentClock', () => {
  const response = { dueAt: DUE, completedAt: null }
  const resolution = { dueAt: '2026-01-05T17:00:00Z', completedAt: null }

  it('uses the response deadline until someone picks it up', () => {
    expect(currentClock({ sla: { response, resolution } })).toEqual({
      kind: 'Respond',
      clock: response,
    })
  })

  it('then uses the resolution deadline', () => {
    const picked = { ...response, completedAt: '2026-01-05T09:30:00Z' }
    expect(currentClock({ sla: { response: picked, resolution } })).toEqual({
      kind: 'Resolve',
      clock: resolution,
    })
  })

  it('is null without SLA data', () => {
    expect(currentClock({})).toBeNull()
  })
})
