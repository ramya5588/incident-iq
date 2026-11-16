import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMyIncidents } from '../services/incidentApi'
import { STATUS_LABELS } from '../utils/roles'
import { incidentLabel } from '../utils/incidentNumbers'

/** A reporter's own incidents, and which ones are waiting on them. */
function MyIncidents() {
  const [incidents, setIncidents] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    getMyIncidents()
      .then((data) => {
        if (!cancelled) setIncidents(data)
      })
      .catch((requestError) => {
        console.error(requestError)
        if (!cancelled) setError('Unable to load your incidents.')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const waitingOnYou = (incident) =>
    incident.status === 'ON_HOLD' && incident.holdReason === 'WAITING_ON_REPORTER'

  return (
    <section className="content-card my-work" aria-labelledby="my-incidents-title">
      <div className="section-heading">
        <div>
          <h2 id="my-incidents-title">Your incidents</h2>
          <p>The incidents you reported, most recently updated first.</p>
        </div>
      </div>

      {error && <p className="error-message">{error}</p>}
      {!incidents && !error && <p className="timeline-empty">Loading…</p>}
      {incidents && incidents.length === 0 && (
        <p className="timeline-empty">You haven't reported any incidents yet.</p>
      )}

      {incidents && incidents.length > 0 && (
        <ul className="my-incidents">
          {incidents.map((incident) => (
            <li key={incident.id}>
              <Link className="my-work-link" to={`/incidents/${incident.id}`}>
                <strong>{incident.title}</strong>
                <small>{incidentLabel(incident.incidentNumber)}</small>
              </Link>
              <span className="status">{STATUS_LABELS[incident.status] ?? incident.status}</span>
              {waitingOnYou(incident) && <span className="waiting-on-you">Waiting on you: reply to continue</span>}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default MyIncidents
