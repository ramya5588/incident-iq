import { useEffect, useState } from 'react'
import Sidebar from '../components/Sidebar'
import { Link } from 'react-router-dom'
import {
  changeUserRole,
  createUser,
  describeApiError,
  getUsers,
  getWorkload,
  resetUserPassword,
} from '../services/incidentApi'
import { useCurrentUser } from '../context/currentUserContext'
import { initials } from '../utils/initials'
import { isAdmin } from '../utils/roles'

const ROLE_OPTIONS = [
  { value: 'USER', label: 'User' },
  { value: 'AGENT', label: 'Agent' },
  { value: 'ADMIN', label: 'Admin' },
]

const EMPTY_MEMBER = { displayName: '', email: '', role: 'AGENT', password: '' }

/**
 * One member. Admins get a role menu (with Save/Cancel, so a stray click
 * doesn't change anyone's access) and a password reset.
 */
function MemberRow({ user, activeCount, admin, isYou, onUpdated }) {
  const [role, setRole] = useState(user.role)
  const [savingRole, setSavingRole] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [newPassword, setNewPassword] = useState('')
  const [savingPassword, setSavingPassword] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const roleChanged = role !== user.role

  async function saveRole() {
    setSavingRole(true)
    setError('')
    setMessage('')
    try {
      const updated = await changeUserRole(user.id, role)
      onUpdated(updated)
      const label =
        ROLE_OPTIONS.find((option) => option.value === updated.role)?.label ??
        updated.role
      setMessage(`${updated.displayName} is now ${label}.`)
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to change the role.'))
      setRole(user.role)
    } finally {
      setSavingRole(false)
    }
  }

  async function savePassword(event) {
    event.preventDefault()
    setSavingPassword(true)
    setError('')
    setMessage('')
    try {
      await resetUserPassword(user.id, newPassword)
      setResetting(false)
      setNewPassword('')
      setMessage(
        `Password reset. Share it with ${user.displayName} securely. They have been signed out everywhere.`
      )
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to reset the password.'))
    } finally {
      setSavingPassword(false)
    }
  }

  return (
    <>
      <div className="table-row team-row">
        <span className="member">
          <span className="avatar member-avatar">
            {initials(user.displayName)}
          </span>
          <strong>
            {user.displayName}
            {isYou && <small className="you-tag">You</small>}
          </strong>
        </span>
        <span className="member-email">{user.email}</span>

        {admin ? (
          <span className="role-edit">
            <select
              value={role}
              onChange={(event) => {
                setRole(event.target.value)
                setMessage('')
                setError('')
              }}
              disabled={savingRole}
              aria-label={`Role for ${user.displayName}`}
            >
              {ROLE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            {roleChanged && (
              <>
                <button
                  type="button"
                  className="primary-button small-button"
                  onClick={saveRole}
                  disabled={savingRole}
                >
                  {savingRole ? 'Saving...' : 'Save'}
                </button>
                <button
                  type="button"
                  className="secondary-button small-button"
                  onClick={() => setRole(user.role)}
                  disabled={savingRole}
                >
                  Cancel
                </button>
              </>
            )}
          </span>
        ) : (
          <span className={`role-badge ${user.role.toLowerCase()}`}>
            {user.role}
          </span>
        )}

        <span className="member-workload">{activeCount}</span>

        {admin && (
          <span>
            {isYou ? (
              <Link className="secondary-button small-button" to="/account">
                Change password
              </Link>
            ) : (
              !resetting && (
                <button
                  type="button"
                  className="secondary-button small-button"
                  onClick={() => {
                    setResetting(true)
                    setMessage('')
                    setError('')
                  }}
                >
                  Reset password
                </button>
              )
            )}
          </span>
        )}
      </div>

      {(resetting || error || message) && (
        <div className="member-panel">
          {resetting && (
            <form className="reset-form" onSubmit={savePassword}>
              <label>
                New password for {user.displayName}
                <input
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  minLength={8}
                  maxLength={72}
                  placeholder="At least 8 characters"
                  autoFocus
                  required
                  disabled={savingPassword}
                />
              </label>
              <button
                type="submit"
                className="primary-button small-button"
                disabled={savingPassword}
              >
                {savingPassword ? 'Saving...' : 'Set password'}
              </button>
              <button
                type="button"
                className="secondary-button small-button"
                onClick={() => {
                  setResetting(false)
                  setNewPassword('')
                }}
                disabled={savingPassword}
              >
                Cancel
              </button>
            </form>
          )}
          {error && <p className="error-message">{error}</p>}
          {message && <p className="status-message">{message}</p>}
        </div>
      )}
    </>
  )
}

function Team() {
  const { addUser, updateUser, refreshCurrentUser, currentUser } =
    useCurrentUser()
  const admin = isAdmin(currentUser)
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

  function handleMemberUpdated(user) {
    setUsers((current) =>
      current.map((existing) => (existing.id === user.id ? user : existing))
    )
    updateUser(user)
    if (user.id === currentUser?.id) refreshCurrentUser()
  }

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
      addUser(created)
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

          {isAdmin(currentUser) && !showAddForm && (
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

        {isAdmin(currentUser) && showAddForm && (
          <section className="content-card add-member-card">
            <div className="section-heading">
              <div>
                <h2>Add team member</h2>
                <p>
                  They can sign in with this email and password, and can be
                  assigned incidents straight away.
                </p>
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
                First password
                <input
                  type="password"
                  autoComplete="new-password"
                  value={newMember.password}
                  onChange={(event) =>
                    updateField('password', event.target.value)
                  }
                  minLength={8}
                  maxLength={72}
                  placeholder="At least 8 characters"
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

          <div className={`incident-table ${admin ? 'team-admin' : ''}`}>
            <div className="table-row table-header team-row">
              <span>Member</span>
              <span>Email</span>
              <span>Role</span>
              <span>Active incidents</span>
              {admin && <span>Manage</span>}
            </div>

            {loading && <div className="table-message">Loading team...</div>}

            {error && (
              <div className="table-message error-message">{error}</div>
            )}

            {!loading &&
              !error &&
              users.map((user) => (
                <MemberRow
                  key={user.id}
                  user={user}
                  activeCount={workload[user.id] ?? 0}
                  admin={admin}
                  isYou={user.id === currentUser?.id}
                  onUpdated={handleMemberUpdated}
                />
              ))}
          </div>
        </section>
      </main>
    </div>
  )
}

export default Team
