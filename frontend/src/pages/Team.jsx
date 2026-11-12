import { useEffect, useState } from 'react'
import Sidebar from '../components/Sidebar'
import {
  createUser,
  describeApiError,
  getUsers,
  getWorkload,
} from '../services/incidentApi'

const ROLE_OPTIONS = [
  { value: 'USER', label: 'User' },
  { value: 'AGENT', label: 'Agent' },
  { value: 'ADMIN', label: 'Admin' },
]

const EMPTY_MEMBER = { displayName: '', email: '', role: 'AGENT' }

function initials(name) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

function Team() {
  const [users, setUsers] = useState([])
  const [workload, setWorkload] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [showAddForm, setShowAddForm] = useState(false)
  const [newMember, setNewMember] = useState(EMPTY_MEMBER)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [formMessage, setFormMessage] = useState('')

  useEffect(() => {
    async function loadTeam() {
      try {
        const [userData, workloadData] = await Promise.all([
          getUsers(),
          getWorkload(),
        ])
        setUsers(userData)
        setWorkload(
          Object.fromEntries(
            workloadData.map((row) => [row.userId, row.activeCount])
          )
        )
      } catch (requestError) {
        console.error(requestError)
        setError('Unable to load the team from the backend.')
      } finally {
        setLoading(false)
      }
    }

    loadTeam()
  }, [])

  function updateField(field, value) {
    setNewMember((current) => ({ ...current, [field]: value }))
  }

  async function handleAddMember(event) {
    event.preventDefault()
    setSubmitting(true)
    setFormError('')
    setFormMessage('')

    try {
      const created = await createUser(newMember)
      setUsers((current) =>
        [...current, created].sort((a, b) =>
          a.displayName.localeCompare(b.displayName)
        )
      )
      setNewMember(EMPTY_MEMBER)
      setShowAddForm(false)
      setFormMessage(`${created.displayName} was added to the team.`)
    } catch (requestError) {
      console.error(requestError)
      setFormError(
        describeApiError(requestError, 'Unable to add the team member.')
      )
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
            <p className="eyebrow">PEOPLE</p>
            <h1>Team</h1>
            <p>Everyone who can report and work on incidents.</p>
          </div>

          {!showAddForm && (
            <button
              className="primary-button"
              onClick={() => {
                setShowAddForm(true)
                setFormMessage('')
              }}
            >
              + Add team member
            </button>
          )}
        </header>

        {formMessage && <p className="status-message">{formMessage}</p>}

        {showAddForm && (
          <section className="content-card add-member-card">
            <div className="section-heading">
              <div>
                <h2>Add team member</h2>
                <p>They can be assigned incidents straight away.</p>
              </div>
            </div>

            <form className="add-member-form" onSubmit={handleAddMember}>
              <label>
                Name
                <input
                  type="text"
                  value={newMember.displayName}
                  onChange={(event) =>
                    updateField('displayName', event.target.value)
                  }
                  maxLength={120}
                  autoFocus
                  required
                />
              </label>

              <label>
                Email
                <input
                  type="email"
                  value={newMember.email}
                  onChange={(event) => updateField('email', event.target.value)}
                  required
                />
              </label>

              <label>
                Role
                <select
                  value={newMember.role}
                  onChange={(event) => updateField('role', event.target.value)}
                >
                  {ROLE_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>

              <div className="form-actions">
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => {
                    setShowAddForm(false)
                    setFormError('')
                    setNewMember(EMPTY_MEMBER)
                  }}
                  disabled={submitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="primary-button"
                  disabled={submitting}
                >
                  {submitting ? 'Adding...' : 'Add member'}
                </button>
              </div>
            </form>

            {formError && <p className="error-message">{formError}</p>}
          </section>
        )}

        <section className="content-card">
          <div className="section-heading">
            <div>
              <h2>Members</h2>
              <p>
                {loading
                  ? 'Loading…'
                  : `${users.length} member${users.length === 1 ? '' : 's'}`}
              </p>
            </div>
          </div>

          <div className="incident-table">
            <div className="table-row table-header team-row">
              <span>Member</span>
              <span>Email</span>
              <span>Role</span>
              <span>Active incidents</span>
            </div>

            {loading && <div className="table-message">Loading team...</div>}

            {error && (
              <div className="table-message error-message">{error}</div>
            )}

            {!loading &&
              !error &&
              users.map((user) => (
                <div className="table-row team-row" key={user.id}>
                  <span className="member">
                    <span className="avatar member-avatar">
                      {initials(user.displayName)}
                    </span>
                    <strong>{user.displayName}</strong>
                  </span>
                  <span className="member-email">{user.email}</span>
                  <span className={`role-badge ${user.role.toLowerCase()}`}>
                    {user.role}
                  </span>
                  <span className="member-workload">
                    {workload[user.id] ?? 0}
                  </span>
                </div>
              ))}
          </div>
        </section>
      </main>
    </div>
  )
}

export default Team
