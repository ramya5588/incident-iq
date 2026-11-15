import { useState } from 'react'
import { bulkUpdateIncidents, describeApiError } from '../services/incidentApi'
import { HOLD_REASONS, STATUS_LABELS, canBeAssigned } from '../utils/roles'

const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const UNASSIGN = 'unassign'

function titleCase(value) {
  return value.charAt(0) + value.slice(1).toLowerCase()
}

/**
 * Changes the status, priority or assignee of the selected incidents at once.
 * `selected` are the incidents (with their numbers, for messages); `onDone`
 * gets a short summary and the failures after the change is saved.
 */
function BulkActionBar({ selected, users, onDone, onClear }) {
  const [status, setStatus] = useState('')
  const [holdReason, setHoldReason] = useState('')
  const [holdNote, setHoldNote] = useState('')
  const [priority, setPriority] = useState('')
  const [assignee, setAssignee] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const assignable = users.filter((user) => canBeAssigned(user) && user.active !== false)
  const nothingChosen = !status && !priority && !assignee
  const needsReason = status === 'ON_HOLD' && !holdReason
  const count = selected.length

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError('')

    const changes = { incidentIds: selected.map((incident) => incident.id) }
    if (status) changes.status = status
    if (status === 'ON_HOLD') {
      changes.holdReason = holdReason
      if (holdNote.trim()) changes.holdNote = holdNote.trim()
    }
    if (priority) changes.priority = priority
    if (assignee === UNASSIGN) changes.unassign = true
    else if (assignee) changes.assigneeId = assignee

    try {
      const result = await bulkUpdateIncidents(changes)
      const numbers = new Map(
        selected.map((incident) => [
          incident.id,
          `INC-${String(incident.incidentNumber).padStart(3, '0')}`,
        ])
      )
      onDone({
        message: `Updated ${result.updated} incident${result.updated === 1 ? '' : 's'}.`,
        failures: result.failed.map(
          (failure) => `${numbers.get(failure.incidentId) ?? 'An incident'}: ${failure.message}`
        ),
      })
      setStatus('')
      setHoldReason('')
      setHoldNote('')
      setPriority('')
      setAssignee('')
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to update the incidents.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="bulk-bar" onSubmit={handleSubmit} aria-label="Change selected incidents">
      <strong className="bulk-count">{count} selected</strong>

      <fieldset disabled={saving}>
        <label>
          Status
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="">No change</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>

        {status === 'ON_HOLD' && (
          <>
            <label>
              Waiting for
              <select value={holdReason} onChange={(event) => setHoldReason(event.target.value)}>
                <option value="">Choose…</option>
                {Object.entries(HOLD_REASONS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Note (optional)
              <input
                value={holdNote}
                maxLength={200}
                onChange={(event) => setHoldNote(event.target.value)}
              />
            </label>
          </>
        )}

        <label>
          Priority
          <select value={priority} onChange={(event) => setPriority(event.target.value)}>
            <option value="">No change</option>
            {PRIORITIES.map((value) => (
              <option key={value} value={value}>
                {titleCase(value)}
              </option>
            ))}
          </select>
        </label>

        <label>
          Assignee
          <select value={assignee} onChange={(event) => setAssignee(event.target.value)}>
            <option value="">No change</option>
            <option value={UNASSIGN}>Unassigned</option>
            {assignable.map((user) => (
              <option key={user.id} value={user.id}>
                {user.displayName}
              </option>
            ))}
          </select>
        </label>

        <button type="submit" className="primary-button" disabled={nothingChosen || needsReason}>
          {saving ? 'Applying…' : `Apply to ${count} incident${count === 1 ? '' : 's'}`}
        </button>
        <button type="button" className="secondary-button" onClick={onClear}>
          Clear selection
        </button>
      </fieldset>

      {error && (
        <p className="error-message" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}

export default BulkActionBar
