// SLA display helpers. The backend sends each deadline as
// { dueAt, completedAt, state }. For deadlines that are still running we
// recompute the state from the current time (same rule as the backend:
// at risk once a quarter or less of the window is left), so badges stay
// correct while a page is left open.

export const SLA_STATE_LABELS = {
  ON_TRACK: 'On track',
  AT_RISK: 'At risk',
  BREACHED: 'Breached',
  MET: 'Met',
  PAUSED: 'Paused',
}

/** State of one deadline at `now` (ms since epoch). */
export function liveState(clock, startedAt, now) {
  if (!clock) return null
  // On hold: the backend moves the deadline; nothing ticks down meanwhile.
  if (clock.completedAt || clock.state === 'PAUSED') return clock.state
  // Business hours: only the server knows which time counts, so keep its
  // state until the deadline itself passes.
  if (clock.businessHours) {
    return now >= Date.parse(clock.dueAt) ? 'BREACHED' : clock.state
  }

  const due = Date.parse(clock.dueAt)
  if (now >= due) return 'BREACHED'

  const total = due - Date.parse(startedAt)
  const left = due - now
  return left * 4 <= total ? 'AT_RISK' : 'ON_TRACK'
}

/** "2h 15m", "3d 4h", "45m", "under 1m". */
export function formatDuration(ms) {
  const minutes = Math.floor(Math.abs(ms) / 60000)
  if (minutes < 1) return 'under 1m'

  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  const mins = minutes % 60

  if (days > 0) return hours > 0 ? `${days}d ${hours}h` : `${days}d`
  if (hours > 0) return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`
  return `${mins}m`
}

/** Short text for one deadline: "2h left", "Overdue 3h", "Met", "Missed by 5m". */
export function describeClock(clock, state, now) {
  if (state === 'PAUSED') return 'Paused'
  const due = Date.parse(clock.dueAt)

  if (clock.completedAt) {
    const completed = Date.parse(clock.completedAt)
    return state === 'MET' ? 'Met' : `Missed by ${formatDuration(completed - due)}`
  }

  if (now >= due) return `Overdue ${formatDuration(now - due)}`

  // Business hours: working time left as of when the page loaded.
  if (clock.businessHours && clock.countedSecondsLeft != null) {
    return `${formatDuration(clock.countedSecondsLeft * 1000)} left (business hours)`
  }
  return `${formatDuration(due - now)} left`
}

/**
 * The deadline that matters most for an incident right now: the response
 * deadline until someone picks it up, then the resolution deadline.
 */
export function currentClock(incident) {
  const sla = incident?.sla
  if (!sla) return null

  return sla.response.completedAt
    ? { kind: 'Resolve', clock: sla.resolution }
    : { kind: 'Respond', clock: sla.response }
}

export function formatDeadline(value) {
  return new Date(value).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}
