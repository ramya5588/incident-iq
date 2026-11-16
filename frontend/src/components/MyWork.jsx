import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { describeApiError, getMyWork, updateIncidentAssignee } from '../services/incidentApi'
import SlaBadge from './SlaBadge'
import { useNow } from '../hooks/useNow'
import { currentClock, formatDuration } from '../utils/sla'
import { HOLD_REASONS } from '../utils/roles'
import { incidentLabel } from '../utils/incidentNumbers'

function IncidentTitle({ incident }) {
  return (
    <Link className="my-work-link" to={`/incidents/${incident.id}`}>
      <strong>{incident.title}</strong>
      <small>{incidentLabel(incident.incidentNumber)}</small>
    </Link>
  )
}

function Panel({ title, count, empty, children }) {
  return (
    <section className="my-work-panel" aria-label={title}>
      <h3>
        {title}
        {count > 0 && <span className="my-work-count">{count}</span>}
      </h3>
      {count === 0 ? <p className="timeline-empty">{empty}</p> : <ul>{children}</ul>}
    </section>
  )
}

/**
 * The agent's "what now?": their incidents by next deadline, the ones on
 * hold, the unassigned queue (with Take it) and unread notifications.
 */
function MyWork({ currentUser }) {
  const now = useNow()
  const [work, setWork] = useState(null)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const [taking, setTaking] = useState(null)
  const [takeError, setTakeError] = useState('')

  useEffect(() => {
    let cancelled = false
    getMyWork()
      .then((data) => {
        if (!cancelled) setWork(data)
      })
      .catch((requestError) => {
        console.error(requestError)
        if (!cancelled) setError('Unable to load your work.')
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  async function take(incident) {
    setTaking(incident.id)
    setTakeError('')
    try {
      await updateIncidentAssignee(incident.id, currentUser.id)
      setReloadKey((key) => key + 1)
    } catch (requestError) {
      console.error(requestError)
      setTakeError(describeApiError(requestError, 'Unable to take it.'))
    } finally {
      setTaking(null)
    }
  }

  return (
    <section className="content-card my-work" aria-labelledby="my-work-title">
      <div className="section-heading">
        <div>
          <h2 id="my-work-title">My work</h2>
          <p>What's assigned to you, what's waiting, and what nobody has picked up.</p>
        </div>
      </div>

      {error && <p className="error-message">{error}</p>}
      {!work && !error && <p className="timeline-empty">Loading your work…</p>}

      {work && (
        <div className="my-work-grid">
          <Panel title="Due soonest" count={work.dueSoon.length} empty="Nothing open is assigned to you.">
            {work.dueSoon.map((incident) => {
              const current = currentClock(incident)
              return (
                <li key={incident.id}>
                  <IncidentTitle incident={incident} />
                  <span className={`badge ${incident.priority.toLowerCase()}`}>{incident.priority}</span>
                  {current && (
                    <SlaBadge clock={current.clock} startedAt={incident.createdAt} now={now} kind={current.kind} />
                  )}
                </li>
              )
            })}
          </Panel>

          <Panel title="Waiting on someone" count={work.waiting.length} empty="None of yours are on hold.">
            {work.waiting.map((incident) => (
              <li key={incident.id}>
                <IncidentTitle incident={incident} />
                <small className="my-work-meta">
                  {HOLD_REASONS[incident.holdReason] ?? 'On hold'}
                  {incident.onHoldSince &&
                    ` · ${formatDuration(now - Date.parse(incident.onHoldSince))}`}
                </small>
              </li>
            ))}
          </Panel>

          <Panel
            title="Unassigned"
            count={work.unassignedTotal}
            empty="Every active incident has someone on it."
          >
            {work.unassigned.map((incident) => (
              <li key={incident.id}>
                <IncidentTitle incident={incident} />
                <span className={`badge ${incident.priority.toLowerCase()}`}>{incident.priority}</span>
                <button
                  type="button"
                  className="secondary-button small-button"
                  onClick={() => take(incident)}
                  disabled={taking !== null}
                  aria-label={`Take ${incidentLabel(incident.incidentNumber)}`}
                >
                  {taking === incident.id ? 'Taking…' : 'Take it'}
                </button>
              </li>
            ))}
            {work.unassignedTotal > work.unassigned.length && (
              <li className="my-work-more">
                <Link to="/incidents">
                  {work.unassignedTotal - work.unassigned.length} more on the incident list
                </Link>
              </li>
            )}
          </Panel>

          <Panel title="Unread updates" count={work.unread.length} empty="You're all caught up.">
            {work.unread.map((item) => (
              <li key={item.incidentId}>
                <Link className="my-work-link" to={`/incidents/${item.incidentId}`}>
                  <strong>{item.title}</strong>
                  <small>{item.message}</small>
                </Link>
              </li>
            ))}
          </Panel>
        </div>
      )}

      {takeError && (
        <p className="error-message" role="alert">
          {takeError}
        </p>
      )}
    </section>
  )
}

export default MyWork
