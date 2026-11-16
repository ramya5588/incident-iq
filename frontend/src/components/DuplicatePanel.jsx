import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  describeApiError,
  getDuplicates,
  getIncidentPage,
  markDuplicate,
  undoDuplicate,
} from '../services/incidentApi'
import { incidentLabel, parseIncidentNumber } from '../utils/incidentNumbers'

/**
 * Duplicate handling on the incident page: a banner when this incident is a
 * duplicate (with Undo for the team), the duplicates merged into it, and for
 * agents and admins a "Mark as duplicate" form.
 */
function DuplicatePanel({ incident, canWork, onChanged }) {
  const [duplicates, setDuplicates] = useState([])
  const [open, setOpen] = useState(false)
  const [numberText, setNumberText] = useState('')
  const [target, setTarget] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    getDuplicates(incident.id)
      .then((data) => {
        if (!cancelled) setDuplicates(data)
      })
      .catch((requestError) => console.error(requestError))
    return () => {
      cancelled = true
    }
  }, [incident.id, incident.updatedAt])

  function close() {
    setOpen(false)
    setNumberText('')
    setTarget(null)
    setError('')
  }

  /** Finds the main incident by number, then asks to confirm. */
  async function findTarget(event) {
    event.preventDefault()
    setError('')
    const number = parseIncidentNumber(numberText)
    if (number == null) {
      setError('Enter an incident number, like INC-009 or 9.')
      return
    }
    if (number === incident.incidentNumber) {
      setError("An incident can't be a duplicate of itself.")
      return
    }
    setBusy(true)
    try {
      const page = await getIncidentPage({ q: `INC-${number}`, size: 5 })
      const found = page.content.find((item) => item.incidentNumber === number)
      if (found) setTarget(found)
      else setError(`There's no ${incidentLabel(number)}.`)
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to look up that incident.'))
    } finally {
      setBusy(false)
    }
  }

  async function merge() {
    setBusy(true)
    setError('')
    try {
      const updated = await markDuplicate(incident.id, target.id)
      close()
      onChanged(updated)
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to mark it as a duplicate.'))
    } finally {
      setBusy(false)
    }
  }

  async function undo() {
    setBusy(true)
    setError('')
    try {
      onChanged(await undoDuplicate(incident.id))
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to undo it.'))
    } finally {
      setBusy(false)
    }
  }

  const label = incidentLabel(incident.incidentNumber)

  return (
    <div className="duplicate-panel">
      {incident.duplicateOfId && (
        <div className="duplicate-banner" role="note">
          <span>
            Closed as a duplicate of{' '}
            <Link to={`/incidents/${incident.duplicateOfId}`}>
              {incidentLabel(incident.duplicateOfNumber)} “{incident.duplicateOfTitle}”
            </Link>
            . Updates continue there.
          </span>
          {canWork && (
            <button type="button" className="secondary-button small-button" onClick={undo} disabled={busy}>
              Undo
            </button>
          )}
        </div>
      )}

      {duplicates.length > 0 && (
        <p className="duplicate-list">
          Merged duplicates:{' '}
          {duplicates.map((item, index) => (
            <span key={item.id}>
              {index > 0 && ', '}
              <Link to={`/incidents/${item.id}`}>
                {incidentLabel(item.incidentNumber)} “{item.title}”
              </Link>
            </span>
          ))}
        </p>
      )}

      {canWork && !incident.duplicateOfId && duplicates.length === 0 && !open && (
        <button type="button" className="link-button" onClick={() => setOpen(true)}>
          Mark as duplicate…
        </button>
      )}

      {open && !target && (
        <form className="duplicate-form" onSubmit={findTarget}>
          <label>
            Duplicate of which incident?
            <input
              value={numberText}
              onChange={(event) => setNumberText(event.target.value)}
              placeholder="INC-009"
              autoFocus
            />
          </label>
          <button type="submit" className="secondary-button" disabled={busy || !numberText.trim()}>
            {busy ? 'Looking…' : 'Find'}
          </button>
          <button type="button" className="secondary-button" onClick={close}>
            Cancel
          </button>
        </form>
      )}

      {open && target && (
        <div className="duplicate-confirm" role="alertdialog" aria-label="Confirm duplicate">
          <p>
            Close {label} as a duplicate of{' '}
            <strong>
              {incidentLabel(target.incidentNumber)} “{target.title}”
            </strong>
            ? Its reporter and watchers will follow {incidentLabel(target.incidentNumber)} instead.
          </p>
          <button type="button" className="primary-button" onClick={merge} disabled={busy}>
            {busy ? 'Merging…' : 'Mark as duplicate'}
          </button>
          <button type="button" className="secondary-button" onClick={close} disabled={busy}>
            Cancel
          </button>
        </div>
      )}

      {error && (
        <p className="error-message" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

export default DuplicatePanel
