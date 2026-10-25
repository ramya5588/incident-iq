import { useEffect, useState } from 'react'
import { getIncidents } from './services/incidentApi'
import './App.css'

function App() {
  const [incidents, setIncidents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const openIncidentCount = incidents.filter(
    (incident) => incident.status === 'OPEN'
  ).length
  const inProgressCount = incidents.filter(
    (incident) => incident.status === 'IN_PROGRESS'
  ).length
  const today = new Date().toISOString().slice(0, 10)

  const resolvedTodayCount = incidents.filter(
    (incident) =>
      incident.status === 'RESOLVED' &&
      incident.resolvedAt?.slice(0, 10) === today
  ).length
  const aiTriagedCount = incidents.filter(
    (incident) => incident.aiTriagedAt
  ).length

  const aiTriagedPercentage =
    incidents.length === 0
      ? 0
      : Math.round((aiTriagedCount / incidents.length) * 100)

  useEffect(() => {
    async function loadIncidents() {
      try {
        const data = await getIncidents()
        setIncidents(data)
      } catch (requestError) {
        console.error(requestError)
        setError('Unable to load incidents from the backend.')
      } finally {
        setLoading(false)
      }
    }

    loadIncidents()
  }, [])

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">IQ</span>

          <div>
            <strong>IncidentIQ</strong>
            <small>Intelligent Operations</small>
          </div>
        </div>

        <nav>
          <button className="nav-item active">Dashboard</button>
          <button className="nav-item">Incidents</button>
          <button className="nav-item">Create incident</button>
          <button className="nav-item">Team</button>
        </nav>

        <div className="sidebar-footer">
          <span className="avatar">RN</span>

          <div>
            <strong>Ramya Navuluri</strong>
            <small>Administrator</small>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="page-header">
          <div>
            <p className="eyebrow">OPERATIONS OVERVIEW</p>
            <h1>Incident dashboard</h1>
            <p>
              Monitor incidents and accelerate resolution with AI triage.
            </p>
          </div>

          <button className="primary-button">+ New incident</button>
        </header>

        <section className="stats-grid">
          <article className="stat-card">
            <span>Open incidents</span>
            <strong>{openIncidentCount}</strong>
            <small>Live data from the backend</small>
          </article>

          <article className="stat-card">
            <span>In progress</span>
            <strong>{inProgressCount}</strong>
            <small>Live data from the backend</small>
          </article>


          <article className="stat-card">
            <span>Resolved today</span>
            <strong>{resolvedTodayCount}</strong>
            <small>Live data from the backend</small>
          </article>

          <article className="stat-card accent-card">
            <span>AI triaged</span>
            <strong>{aiTriagedPercentage}%</strong>
            <small>of loaded incidents</small>
          </article>
        </section>

        <section className="content-card">
          <div className="section-heading">
            <div>
              <h2>Recent incidents</h2>
              <p>Latest incidents across your support environment</p>
            </div>

            <button className="secondary-button">View all</button>
          </div>

          <div className="incident-table">
            <div className="table-row table-header">
              <span>Incident</span>
              <span>Category</span>
              <span>Priority</span>
              <span>Status</span>
            </div>

            {loading && (
              <div className="table-message">Loading incidents...</div>
            )}

            {error && (
              <div className="table-message error-message">{error}</div>
            )}

            {!loading && !error && incidents.length === 0 && (
              <div className="table-message">
                No incidents have been created yet.
              </div>
            )}

            {!loading &&
              !error &&
              incidents.map((incident) => (
                <div className="table-row" key={incident.id}>
                  <div>
                    <strong>{incident.title}</strong>
                    <small>
                      INC-{String(incident.incidentNumber).padStart(3, '0')}
                    </small>
                  </div>

                  <span>{incident.category}</span>

                  <span
                    className={`badge ${(
                      incident.priority ?? 'medium'
                    ).toLowerCase()}`}
                  >
                    {incident.priority}
                  </span>

                  <span className="status">
                    {(incident.status ?? 'OPEN').replaceAll('_', ' ')}
                  </span>
                </div>
              ))}
          </div>
        </section>
      </main>
    </div>
  )
}

export default App