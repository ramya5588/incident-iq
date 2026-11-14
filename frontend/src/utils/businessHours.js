// Display helpers for SLA business hours.

export const DAYS = [
  ['MONDAY', 'Mon'],
  ['TUESDAY', 'Tue'],
  ['WEDNESDAY', 'Wed'],
  ['THURSDAY', 'Thu'],
  ['FRIDAY', 'Fri'],
  ['SATURDAY', 'Sat'],
  ['SUNDAY', 'Sun'],
]

export const PRIORITIES = [
  ['CRITICAL', 'Critical'],
  ['HIGH', 'High'],
  ['MEDIUM', 'Medium'],
  ['LOW', 'Low'],
]

export function timeZones() {
  try {
    return Intl.supportedValuesOf('timeZone')
  } catch {
    return []
  }
}

/** "Mon–Fri" for a run of days, otherwise "Mon, Wed, Sat". */
function describeDays(days) {
  const order = DAYS.map(([value]) => value)
  const picked = order.filter((day) => days.includes(day))
  const label = (day) => DAYS.find(([value]) => value === day)[1]
  const indexes = picked.map((day) => order.indexOf(day))
  const consecutive =
    picked.length > 2 && indexes.every((index, i) => i === 0 || index === indexes[i - 1] + 1)
  return consecutive
    ? `${label(picked[0])}–${label(picked[picked.length - 1])}`
    : picked.map(label).join(', ')
}

function describePriorities(priorities) {
  return PRIORITIES.filter(([value]) => priorities.includes(value))
    .map(([, label]) => label)
    .join(', ')
}

/** One line for everyone: what the SLA deadlines count right now. */
export function describeBusinessHours(settings) {
  if (!settings.enabled || settings.priorities.length === 0) {
    return 'SLA deadlines run around the clock.'
  }
  const holidays = settings.holidays.length
  return (
    `${describePriorities(settings.priorities)} deadlines count business hours only: ` +
    `${describeDays(settings.workingDays)} ${settings.dayStart}–${settings.dayEnd} ` +
    `(${settings.timeZone})` +
    (holidays > 0 ? `, except ${holidays} holiday${holidays === 1 ? '' : 's'}.` : '.')
  )
}
