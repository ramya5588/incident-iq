import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import { getIncidentPage } from '../services/incidentApi'

const PAGE_SIZE = 10

const STATUS_OPTIONS = [
  { value: 'OPEN', label: 'Open' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'CLOSED', label: 'Closed' },
]

const PRIORITY_OPTIONS = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
  { value: 'CRITICAL', label: 'Critical' },
]

function IncidentsList() {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = searchParams.get('status') ?? ''
  const priority = searchParams.get('priority') ?? ''
  const page = Math.max(Number(searchParams.get('page') ?? 0) || 0, 0)

  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false

    async function loadIncidents() {
      setLoading(true)
      setError('')

      try {
        const data = await getIncidentPage({
          status,
          priority,
          page,
          size: PAGE_SIZE,
        })
        if (!cancelled) setResult(data)
      } catch (requestError) {
        console.error(requestError)
        if (!cancelled) setError('Unable to load incidents from the backend.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadIncidents()

    return () => {
      cancelled = true
    }
  }, [status, priority, page])

  function updateParams(changes) {
    const next = new URLSearchParams(searchParams)

    Object.entries(changes).forEach(([key, value]) => {
      if (value === '' || value === null || value === undefined) {
        next.delete(key)
      } else {
        next.set(key, String(value))
      }
    })

    setSearchParams(next)
  }

  const incidents = result?.content ?? []
  const totalElements = result?.totalElements ?? 0
  const totalPages = result?.totalPages ?? 0
  const hasFilters = status !== '' || priority !== ''
  const firstShown = totalElements === 0 ? 0 : page * PAGE_SIZE + 1
  const lastShown = page * PAGE_SIZE + incidents.length

  return (
    <div className="app-shell">
      <Sidebar />

      <main className="main-content">
        <header className="page-header">
          <div>
            <p className="eyebrow">ALL INCIDENTS</p>
            <h1>Incidents</h1>
            <p>Browse, filter and open every incident in your environment.</p>
          </div>
        </header>

        <section className="content-card">
          <div className="list-toolbar">
            <label>
              Status
              <select
                value={status}
                onChange={(event) =>
                  updateParams({ status: event.target.value, page: '' })
                }
              >
                <option value="">All statuses</option>
                {STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Priority
              <select
                value={priority}
                onChange={(event) =>
                  updateParams({ priority: event.target.value, page: '' })
                }
              >
                <option value="">All priorities</option>
                {PRIORITY_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            {hasFilters && (
              <button
                type="button"
                className="secondary-button"
                onClick={() => setSearchParams(new URLSearchParams())}
              >
                Clear filters
              </button>
            )}

            <span className="list-count">
              {loading
                ? 'Loading...'
                : `${totalElements} incident${totalElements === 1 ? '' : 's'}`}
            </span>
          </div>

          <div className="incident-table">
            <div className="table-row table-header list-row">
              <span>Incident</span>
              <span>Category</span>
              <span>Priority</span>
              <span>Status</span>
              <span>Created</span>
            </div>

            {loading && (
              <div className="table-message">Loading incidents...</div>
            )}

            {error && (
              <div className="table-message error-message">{error}</div>
            )}

            {!loading && !error && incidents.length === 0 && (
              <div className="table-message">
                {hasFilters
                  ? 'No incidents match these filters.'
                  : 'No incidents have been created yet.'}
              </div>
            )}

            {!loading &&
              !error &&
              incidents.map((incident) => (
                <div className="table-row list-row" key={incident.id}>
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

                  <span className="created-at">
                    {incident.createdAt
                      ? new Date(incident.createdAt).toLocaleDateString()
                      : '—'}
                  </span>
                </div>
              ))}
          </div>

          {!error && totalPages > 1 && (
            <div className="pagination">
              <span>
                Showing {firstShown}–{lastShown} of {totalElements}
              </span>

              <div>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={loading || page === 0}
                  onClick={() => updateParams({ page: page - 1 || '' })}
                >
                  Previous
                </button>
                <span>
                  Page {page + 1} of {totalPages}
                </span>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={loading || page + 1 >= totalPages}
                  onClick={() => updateParams({ page: page + 1 })}
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </section>
      </main>
    </div>
  )
}

export default IncidentsList
