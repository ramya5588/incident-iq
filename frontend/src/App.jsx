import { useEffect, useState } from 'react'
import {
  createIncident,
  getIncidentPage,
  getIncidentStats,
} from './services/incidentApi'
import './App.css'
import { Link, Routes, Route } from 'react-router-dom'
import IncidentDetails from './pages/IncidentDetails'
import IncidentsList from './pages/IncidentsList'
import Sidebar from './components/Sidebar'

const RECENT_INCIDENT_LIMIT = 5

function Dashboard() {
  const [incidents, setIncidents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [stats, setStats] = useState(null)
  const [statsError, setStatsError] = useState(false)
  const [showCreateForm, setShowCreateForm] = useState(false)

  const [newIncident, setNewIncident] = useState({
    title: '',
    description: '',
    priority: 'MEDIUM',
    category: '',
    createdById: '918bface-4f32-4450-9507-588685f3392b',
  })

  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')

  async function loadStats() {
    try {
      setStats(await getIncidentStats())
      setStatsError(false)
    } catch (requestError) {
      console.error(requestError)
      setStatsError(true)
    }
  }

  function statValue(value, suffix = '') {
    if (statsError) return '—'
    if (stats === null) return '…'
    return `${value}${suffix}`
  }

  useEffect(() => {
    async function loadIncidents() {
      try {
        const data = await getIncidentPage({ size: RECENT_INCIDENT_LIMIT })
        setIncidents(data.content ?? [])
      } catch (requestError) {
        console.error(requestError)
        setError('Unable to load incidents from the backend.')
      } finally {
        setLoading(false)
      }
    }

    async function loadInitialStats() {
      try {
        setStats(await getIncidentStats())
      } catch (requestError) {
        console.error(requestError)
        setStatsError(true)
      }
    }

    loadIncidents()
    loadInitialStats()
  }, [])
  async function handleCreateIncident(event) {
    event.preventDefault()
    setSubmitting(true)
    setFormError('')

    try {
      const createdIncident = await createIncident(newIncident)

      setIncidents((currentIncidents) =>
        [createdIncident, ...currentIncidents].slice(0, RECENT_INCIDENT_LIMIT)
      )
      loadStats()

      setShowCreateForm(false)

      setNewIncident({
        title: '',
        description: '',
        priority: 'MEDIUM',
        category: '',
        createdById: '918bface-4f32-4450-9507-588685f3392b',
      })
    } catch (requestError) {
      console.error(requestError)
      setFormError('Unable to create the incident.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="app-shell">
      <Sidebar />

      <main className="main-content">
        <header className="page-header">
          <div>
            <p className="eyebrow">OPERATIONS OVERVIEW</p>
            <h1>Incident dashboard</h1>
            <p>
              Monitor incidents and accelerate resolution with AI triage.
            </p>
          </div>

          <button
            className="primary-button"
            onClick={() => setShowCreateForm(true)}
          >
            + New incident
          </button>        </header>

        <section className="stats-grid">
          <article className="stat-card">
            <span>Open incidents</span>
            <strong>{statValue(stats?.open)}</strong>
            <small>
              {statsError ? 'Unable to load stats' : 'Awaiting action'}
            </small>
          </article>

          <article className="stat-card">
            <span>In progress</span>
            <strong>{statValue(stats?.inProgress)}</strong>
            <small>Being worked on</small>
          </article>

          <article className="stat-card">
            <span>Resolved today</span>
            <strong>{statValue(stats?.resolvedInWindow)}</strong>
            <small>Since midnight, your time</small>
          </article>

          <article className="stat-card accent-card">
            <span>AI triaged</span>
            <strong>{statValue(stats?.aiTriagedPercentage, '%')}</strong>
            <small>
              {stats ? `of all ${stats.total} incidents` : 'of all incidents'}
            </small>
          </article>
        </section>

        <section className="content-card">
          <div className="section-heading">
            <div>
              <h2>Recent incidents</h2>
              <p>
                The {RECENT_INCIDENT_LIMIT} latest incidents across your
                support environment
              </p>
            </div>

            <Link className="secondary-button" to="/incidents">
              View all
            </Link>
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
                  <Link
                    className="incident-link"
                    to={`/incidents/${incident.id}`}
                  >
                    <strong>{incident.title}</strong>
                    <small>
                      INC-{String(incident.incidentNumber).padStart(3, '0')}
                    </small>
                  </Link>

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
      {showCreateForm && (
        <div className="modal-backdrop">
          <section className="modal-card">
            <div className="modal-header">
              <div>
                <p className="eyebrow">NEW REQUEST</p>
                <h2>Create incident</h2>
              </div>

              <button
                type="button"
                className="close-button"
                onClick={() => setShowCreateForm(false)}
              >
                ×
              </button>
            </div>

            <form onSubmit={handleCreateIncident}>
              <label>
                Title
                <input
                  type="text"
                  value={newIncident.title}
                  onChange={(event) =>
                    setNewIncident({
                      ...newIncident,
                      title: event.target.value,
                    })
                  }
                  required
                />
              </label>

              <label>
                Description
                <textarea
                  rows="5"
                  value={newIncident.description}
                  onChange={(event) =>
                    setNewIncident({
                      ...newIncident,
                      description: event.target.value,
                    })
                  }
                  required
                />
              </label>

              <div className="form-grid">
                <label>
                  Priority
                  <select
                    value={newIncident.priority}
                    onChange={(event) =>
                      setNewIncident({
                        ...newIncident,
                        priority: event.target.value,
                      })
                    }
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                    <option value="CRITICAL">Critical</option>
                  </select>
                </label>

                <label>
                  Category
                  <input
                    type="text"
                    placeholder="Authentication"
                    value={newIncident.category}
                    onChange={(event) =>
                      setNewIncident({
                        ...newIncident,
                        category: event.target.value,
                      })
                    }
                    required
                  />
                </label>
              </div>

              {formError && <p className="form-error">{formError}</p>}

              <div className="form-actions">
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => setShowCreateForm(false)}
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  className="primary-button"
                  disabled={submitting}
                >
                  {submitting ? 'Creating...' : 'Create incident'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="/incidents" element={<IncidentsList />} />
      <Route path="/incidents/:id" element={<IncidentDetails />} />
    </Routes>
  )
}

export default App