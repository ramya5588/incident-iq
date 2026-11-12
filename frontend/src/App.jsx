import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  getIncidentPage,
  getIncidentStats,
  getCurrentUser,
  getUsers,
  onUnauthorized,
  signOut as signOutRequest,
} from './services/incidentApi'
import { CurrentUserContext } from './context/currentUserContext'
import './App.css'
import { Link, Routes, Route, useNavigate } from 'react-router-dom'
import IncidentDetails from './pages/IncidentDetails'
import IncidentsList from './pages/IncidentsList'
import Team from './pages/Team'
import SignInPage from './pages/SignInPage'
import AccountPage from './pages/AccountPage'
import Sidebar from './components/Sidebar'
import CreateIncidentModal from './components/CreateIncidentModal'
import {
  CreateIncidentContext,
  useCreateIncident,
} from './context/createIncidentContext'

const RECENT_INCIDENT_LIMIT = 5

function Dashboard() {
  const openCreateIncident = useCreateIncident()
  const [incidents, setIncidents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [stats, setStats] = useState(null)
  const [statsError, setStatsError] = useState(false)

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

    async function loadStats() {
      try {
        setStats(await getIncidentStats())
      } catch (requestError) {
        console.error(requestError)
        setStatsError(true)
      }
    }

    loadIncidents()
    loadStats()
  }, [])

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

          <button className="primary-button" onClick={openCreateIncident}>
            + New incident
          </button>
        </header>

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

          <Link
            className={`stat-card stat-link ${
              stats?.slaBreached > 0 ? 'danger-card' : ''
            }`}
            to="/incidents?overdue=true"
          >
            <span>SLA breached</span>
            <strong>{statValue(stats?.slaBreached)}</strong>
            <small>Open and past a deadline</small>
          </Link>

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
    </div>
  )
}

function App() {
  const navigate = useNavigate()
  const [showCreateIncident, setShowCreateIncident] = useState(false)

  // undefined = still checking the session, null = signed out.
  const [currentUser, setCurrentUser] = useState(undefined)
  const [users, setUsers] = useState([])
  const [usersError, setUsersError] = useState('')

  const handleSignedOut = useCallback(() => {
    setCurrentUser(null)
    setUsers([])
    setUsersError('')
    setShowCreateIncident(false)
  }, [])

  useEffect(() => {
    let cancelled = false

    async function checkSession() {
      try {
        const user = await getCurrentUser()
        if (!cancelled) setCurrentUser(user)
      } catch (requestError) {
        console.error(requestError)
        if (!cancelled) setCurrentUser(null)
      }
    }

    checkSession()
    // Any later 401 (expired session, signed out elsewhere) shows sign-in.
    onUnauthorized(handleSignedOut)

    return () => {
      cancelled = true
      onUnauthorized(null)
    }
  }, [handleSignedOut])

  const signedInId = currentUser?.id
  useEffect(() => {
    if (!signedInId) return undefined
    let cancelled = false

    async function loadUsers() {
      try {
        const data = await getUsers()
        if (!cancelled) {
          setUsers(data)
          setUsersError('')
        }
      } catch (requestError) {
        console.error(requestError)
        if (!cancelled) setUsersError('Unable to load users.')
      }
    }

    loadUsers()
    return () => {
      cancelled = true
    }
  }, [signedInId])

  const handleSignOut = useCallback(async () => {
    try {
      await signOutRequest()
    } catch (requestError) {
      // Still drop local state; the session may already be gone.
      console.error(requestError)
    }
    handleSignedOut()
  }, [handleSignedOut])

  const addUser = useCallback((user) => {
    setUsers((current) =>
      [...current, user].sort((a, b) =>
        a.displayName.localeCompare(b.displayName)
      )
    )
  }, [])

  const updateUser = useCallback((user) => {
    setUsers((current) =>
      current.map((existing) => (existing.id === user.id ? user : existing))
    )
  }, [])

  // Re-read who's signed in (e.g. after an admin changes their own role).
  const refreshCurrentUser = useCallback(async () => {
    try {
      setCurrentUser(await getCurrentUser())
    } catch (requestError) {
      console.error(requestError)
    }
  }, [])

  const currentUserValue = useMemo(
    () => ({
      users,
      usersError,
      currentUser: currentUser ?? null,
      signOut: handleSignOut,
      addUser,
      updateUser,
      refreshCurrentUser,
    }),
    [
      users,
      usersError,
      currentUser,
      handleSignOut,
      addUser,
      updateUser,
      refreshCurrentUser,
    ]
  )

  const openCreateIncident = useCallback(() => setShowCreateIncident(true), [])
  const closeCreateIncident = useCallback(
    () => setShowCreateIncident(false),
    []
  )

  const contextValue = useMemo(
    () => ({ openCreateIncident }),
    [openCreateIncident]
  )

  function handleCreated(incident) {
    setShowCreateIncident(false)
    navigate(`/incidents/${incident.id}`)
  }

  if (currentUser === undefined) {
    return <div className="auth-loading">Loading IncidentIQ…</div>
  }

  if (currentUser === null) {
    return <SignInPage onSignedIn={setCurrentUser} />
  }

  return (
    <CurrentUserContext.Provider value={currentUserValue}>
      <CreateIncidentContext.Provider value={contextValue}>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/incidents" element={<IncidentsList />} />
          <Route path="/incidents/:id" element={<IncidentDetails />} />
          <Route path="/team" element={<Team />} />
          <Route path="/account" element={<AccountPage />} />
        </Routes>

        {showCreateIncident && (
          <CreateIncidentModal
            onClose={closeCreateIncident}
            onCreated={handleCreated}
          />
        )}
      </CreateIncidentContext.Provider>
    </CurrentUserContext.Provider>
  )
}

export default App
