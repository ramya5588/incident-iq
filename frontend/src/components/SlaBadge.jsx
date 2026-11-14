import {
  SLA_STATE_LABELS,
  describeClock,
  formatDeadline,
  liveState,
} from '../utils/sla'

/**
 * One SLA deadline as a coloured badge, e.g. "Respond · 45m left".
 * `kind` is an optional prefix ("Respond", "Resolve").
 */
function SlaBadge({ clock, startedAt, now, kind }) {
  if (!clock) return null

  const state = liveState(clock, startedAt, now)
  const text = describeClock(clock, state, now)
  const title =
    state === 'PAUSED'
      ? 'Paused while on hold. The deadline moves later until work resumes.'
      : `${SLA_STATE_LABELS[state]}. Due ${formatDeadline(clock.dueAt)}` +
        (clock.businessHours ? '. Only business hours count.' : '')

  return (
    <span
      className={`sla-badge ${state.toLowerCase().replace('_', '-')}`}
      title={title}
    >
      {kind && <span className="sla-kind">{kind}</span>}
      {text}
    </span>
  )
}

export default SlaBadge
