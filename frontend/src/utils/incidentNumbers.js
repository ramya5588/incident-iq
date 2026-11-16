/** "INC-007". */
export function incidentLabel(number) {
  return `INC-${String(number).padStart(3, '0')}`
}

/** 9 from "INC-009", "inc 9", "#9" or "9"; null if it isn't an incident number. */
export function parseIncidentNumber(text) {
  const match = text.trim().match(/^(?:inc[-\s]?|#)?0*(\d{1,9})$/i)
  if (!match) return null
  const number = Number(match[1])
  return number > 0 ? number : null
}
