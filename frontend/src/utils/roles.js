// Mirrors the backend's rules (SecurityConfig). The backend is the real
// gate; these only decide which controls to show.

/** Agents and admins can edit, change status, assign and triage. */
export function canWorkIncidents(user) {
  return user?.role === 'AGENT' || user?.role === 'ADMIN'
}

/** Only admins can add team members. */
export function isAdmin(user) {
  return user?.role === 'ADMIN'
}

/** Only agents and admins can be assigned incidents. */
export function canBeAssigned(user) {
  return canWorkIncidents(user)
}

export const STATUS_LABELS = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  ON_HOLD: 'On hold',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
}

/** Why an incident is on hold (the backend's HoldReason). */
export const HOLD_REASONS = {
  WAITING_ON_REPORTER: 'Waiting on reporter',
  WAITING_ON_THIRD_PARTY: 'Waiting on third party',
  SCHEDULED: 'Scheduled for later',
  OTHER: 'Other',
}
