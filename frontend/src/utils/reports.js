// Helpers for the Reports page.

export const RANGE_PRESETS = [
  { value: '7', label: 'Last 7 days', days: 7 },
  { value: '30', label: 'Last 30 days', days: 30 },
  { value: '90', label: 'Last 90 days', days: 90 },
  { value: 'custom', label: 'Custom range' },
]

/** "2026-01-07" for a Date, in the browser's time zone. */
export function isoDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** The last `days` days up to and including `today`, as { from, to }. */
export function lastDays(days, today = new Date()) {
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (days - 1))
  return { from: isoDate(start), to: isoDate(today) }
}

/** The viewer's IANA time zone, e.g. "Europe/London" (UTC if unknown). */
export function browserTimeZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}

/** Share met, as a whole percentage, or null when nothing is decided yet. */
export function metRate({ met, missed }) {
  const decided = met + missed
  return decided === 0 ? null : Math.round((met / decided) * 100)
}

/** "Jan 5" for "2026-01-05" (a date, so no time-zone shift). */
export function shortDate(value) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
  })
}

/** A tidy axis maximum and ticks for counts: 0..max in 4 or fewer steps. */
export function countTicks(maxValue) {
  if (maxValue <= 0) return { max: 1, ticks: [0, 1] }
  const rough = maxValue / 4
  const magnitude = 10 ** Math.floor(Math.log10(rough))
  const step = [1, 2, 5, 10].map((m) => m * magnitude).find((s) => s >= rough)
  const wholeStep = Math.max(1, Math.round(step))
  const max = Math.ceil(maxValue / wholeStep) * wholeStep
  const ticks = []
  for (let value = 0; value <= max; value += wholeStep) ticks.push(value)
  return { max, ticks }
}
