import { useEffect, useState } from 'react'
import Sidebar from '../components/Sidebar'
import { Link } from 'react-router-dom'
import {
  changeUserRole,
  createUser,
  deactivateUser,
  describeApiError,
  getUsers,
  getWorkload,
  reactivateUser,
  resetUserPassword,
  sendPasswordResetLink,
  getPasswordResetAvailability,
  updateUserDetails,
  unlockUser,
} from '../services/incidentApi'
import BusinessHoursSettings from '../components/BusinessHoursSettings'
import { useCurrentUser } from '../context/currentUserContext'
import { initials } from '../utils/initials'
import { isAdmin } from '../utils/roles'
import { useNow } from '../hooks/useNow'

const ROLE_OPTIONS = [
  { value: 'USER', label: 'User' },
  { value: 'AGENT', label: 'Agent' },
  { value: 'ADMIN', label: 'Admin' },
]

const EMPTY_MEMBER = { displayName: '', email: '', role: 'AGENT', password: '' }

/**
 * One member. Admins can change the role (Save/Cancel, so a stray click
 * doesn't change anyone's access), edit name and email, reset the password,
 * and deactivate or reactivate. Only one of those panels is open at a time.
 */
function MemberRow({ user, activeCount, admin, isYou, canEmailReset, onUpdated }) {
  const now = useNow()
  // Locks clear themselves; only show one that's still in force.
  const locked = user.lockedUntil != null && Date.parse(user.lockedUntil) > now
  const lockedUntilText = locked
    ? new Date(user.lockedUntil).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    : ''
  const [role, setRole] = useState(user.role)
  const [savingRole, setSavingRole] = useState(false)
  const [panel, setPanel] = useState(null) // 'edit' | 'reset' | 'deactivate'
  const [details, setDetails] = useState({ displayName: '', email: '' })
  const [newPassword, setNewPassword] = useState('')
  const [unassign, setUnassign] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const active = user.active !== false
  const roleChanged = role !== user.role

  function openPanel(name) {
    setPanel(name)
    setError('')
    setMessage('')
    if (name === 'edit') {
      setDetails({ displayName: user.displayName, email: user.email })
    }
    if (name === 'deactivate') setUnassign(activeCount > 0)
  }

  function closePanel() {
    setPanel(null)
    setNewPassword('')
  }

  async function run(action, fallback) {
    setBusy(true)
    setError('')
    setMessage('')
    try {
      await action()
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, fallback))
    } finally {
      setBusy(false)
    }
  }

  function saveRole() {
    setSavingRole(true)
    run(async () => {
      try {
        const updated = await changeUserRole(user.id, role)
        onUpdated(updated)
        const label =
          ROLE_OPTIONS.find((option) => option.value === updated.role)?.label ??
          updated.role
        setMessage(`${updated.displayName} is now ${label}.`)
      } catch (requestError) {
        setRole(user.role)
        throw requestError
      } finally {
        setSavingRole(false)
      }
    }, 'Unable to change the role.')
  }

  function saveDetails(event) {
    event.preventDefault()
    const changes = {}
    if (details.displayName.trim() !== user.displayName) {
      changes.displayName = details.displayName
    }
    if (details.email.trim().toLowerCase() !== user.email) {
      changes.email = details.email
    }
    if (Object.keys(changes).length === 0) {
      closePanel()
      return
    }
    run(async () => {
      const updated = await updateUserDetails(user.id, changes)
      onUpdated(updated)
      closePanel()
      setMessage('Details saved.')
    }, 'Unable to save the details.')
  }

  function savePassword(event) {
    event.preventDefault()
    run(async () => {
      await resetUserPassword(user.id, newPassword)
      closePanel()
      setMessage(
        `Password reset. Share it with ${user.displayName} securely. They have been signed out everywhere.`
      )
    }, 'Unable to reset the password.')
  }

  function emailResetLink() {
    run(async () => {
      await sendPasswordResetLink(user.id)
      closePanel()
      setMessage(
        `Reset link sent to ${user.email}. It works once and expires in 30 minutes.`
      )
    }, 'Unable to send the reset link.')
  }

  function confirmDeactivate() {
    run(async () => {
      const result = await deactivateUser(user.id, unassign)
      onUpdated(result.user, { unassigned: result.unassignedIncidents })
      closePanel()
      const moved = result.unassignedIncidents
      setMessage(
        `${user.displayName} was deactivated and signed out.` +
          (moved > 0
            ? ` ${moved} incident${moved === 1 ? ' was' : 's were'} returned to the queue.`
            : '')
      )
    }, 'Unable to deactivate this member.')
  }

  function unlock() {
    run(async () => {
      const updated = await unlockUser(user.id)
      onUpdated(updated)
      setMessage(`${updated.displayName} can sign in again.`)
    }, 'Unable to unlock this member.')
  }

  function reactivate() {
    run(async () => {
      const updated = await reactivateUser(user.id)
      onUpdated(updated)
      setMessage(`${updated.displayName} can sign in again.`)
    }, 'Unable to reactivate this member.')
  }

  return (
    <>
      <div className={`table-row team-row ${active ? '' : 'inactive'}`}>
        <span className="member">
          <span className="avatar member-avatar">
            {initials(user.displayName)}
          </span>
          <strong>
            {user.displayName}
            {isYou && <small className="you-tag">You</small>}
            {!active && <small className="inactive-tag">Deactivated</small>}
            {active && locked && (
              <small className="locked-tag" title={`Too many failed sign-ins. Locked until ${lockedUntilText}.`}>
                Locked until {lockedUntilText}
              </small>
            )}
          </strong>
        </span>
        <span className="member-email">{user.email}</span>

        {admin && active ? (
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
          <span className="member-actions">
            {active ? (
              <>
                <button
                  type="button"
                  className="secondary-button small-button"
                  onClick={() => openPanel('edit')}
                  aria-label={`Edit ${user.displayName}`}
                >
                  Edit
                </button>
                {locked && (
                  <button
                    type="button"
                    className="secondary-button small-button"
                    onClick={unlock}
                    disabled={busy}
                    aria-label={`Unlock ${user.displayName}`}
                  >
                    {busy ? 'Unlocking...' : 'Unlock'}
                  </button>
                )}
                {isYou ? (
                  <Link className="secondary-button small-button" to="/account">
                    Change password
                  </Link>
                ) : (
                  <>
                    <button
                      type="button"
                      className="secondary-button small-button"
                      onClick={() => openPanel('reset')}
                      aria-label={`Reset password for ${user.displayName}`}
                    >
                      Reset password
                    </button>
                    <button
                      type="button"
                      className="danger-button small-button"
                      onClick={() => openPanel('deactivate')}
                      aria-label={`Deactivate ${user.displayName}`}
                    >
                      Deactivate
                    </button>
                  </>
                )}
              </>
            ) : (
              <button
                type="button"
                className="secondary-button small-button"
                onClick={reactivate}
                disabled={busy}
                aria-label={`Reactivate ${user.displayName}`}
              >
                {busy ? 'Reactivating...' : 'Reactivate'}
              </button>
            )}
          </span>
        )}
      </div>

      {(panel || error || message) && (
        <div className="member-panel">
          {panel === 'edit' && (
            <form className="reset-form" onSubmit={saveDetails}>
              <label>
                Name
                <input
                  type="text"
                  value={details.displayName}
                  onChange={(event) =>
                    setDetails((current) => ({
                      ...current,
                      displayName: event.target.value,
                    }))
                  }
                  maxLength={120}
                  autoFocus
                  required
                  disabled={busy}
                />
              </label>
              <label>
                Email
                <input
                  type="email"
                  value={details.email}
                  onChange={(event) =>
                    setDetails((current) => ({
                      ...current,
                      email: event.target.value,
                    }))
                  }
                  maxLength={255}
                  required
                  disabled={busy}
                />
              </label>
              <button
                type="submit"
                className="primary-button small-button"
                disabled={busy}
              >
                {busy ? 'Saving...' : 'Save details'}
              </button>
              <button
                type="button"
                className="secondary-button small-button"
                onClick={closePanel}
                disabled={busy}
              >
                Cancel
              </button>
            </form>
          )}

          {panel === 'reset' && canEmailReset && (
            <div className="reset-email">
              <button
                type="button"
                className="primary-button small-button"
                onClick={emailResetLink}
                disabled={busy}
              >
                Email {user.displayName} a reset link
              </button>
              <span>or set a new password for them:</span>
            </div>
          )}

          {panel === 'reset' && (
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
                  disabled={busy}
                />
              </label>
              <button
                type="submit"
                className="primary-button small-button"
                disabled={busy}
              >
                {busy ? 'Saving...' : 'Set password'}
              </button>
              <button
                type="button"
                className="secondary-button small-button"
                onClick={closePanel}
                disabled={busy}
              >
                Cancel
              </button>
            </form>
          )}

          {panel === 'deactivate' && (
            <div className="deactivate-panel" role="group" aria-label="Confirm deactivation">
              <p>
                <strong>Deactivate {user.displayName}?</strong> They'll be
                signed out straight away and won't be able to sign in, be
                assigned incidents or get notifications. Their history stays,
                and you can reactivate them later.
              </p>
              {activeCount > 0 && (
                <label className="checkbox-filter">
                  <input
                    type="checkbox"
                    checked={unassign}
                    onChange={(event) => setUnassign(event.target.checked)}
                    disabled={busy}
                  />
                  Also unassign their {activeCount} open incident
                  {activeCount === 1 ? '' : 's'} (return them to the queue)
                </label>
              )}
              <div className="deactivate-actions">
                <button
                  type="button"
                  className="danger-button small-button"
                  onClick={confirmDeactivate}
                  disabled={busy}
                >
                  {busy ? 'Deactivating...' : 'Deactivate'}
                </button>
                <button
                  type="button"
                  className="secondary-button small-button"
                  onClick={closePanel}
                  disabled={busy}
                >
                  Cancel
                </button>
              </div>
            </div>
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
  const [showDeactivated, setShowDeactivated] = useState(false)
  const [canEmailReset, setCanEmailReset] = useState(false)

  const deactivatedCount = users.filter((user) => user.active === false).length
  const activeCount = users.length - deactivatedCount
  const visibleUsers = showDeactivated
    ? users
    : users.filter((user) => user.active !== false)

  // Reset links need email; without it admins set passwords directly.
  useEffect(() => {
    if (!admin) return
    let cancelled = false
    getPasswordResetAvailability()
      .then((available) => !cancelled && setCanEmailReset(available))
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [admin])

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

  function handleMemberUpdated(user, { unassigned = 0 } = {}) {
    setUsers((current) =>
      current.map((existing) => (existing.id === user.id ? user : existing))
    )
    if (unassigned > 0) {
      setWorkload((current) => ({ ...current, [user.id]: 0 }))
    }
    // Keep a just-deactivated member (and the confirmation) on screen.
    if (user.active === false) setShowDeactivated(true)
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
                  : `${activeCount} active member${activeCount === 1 ? '' : 's'}` +
                    (deactivatedCount > 0 ? ` · ${deactivatedCount} deactivated` : '')}
              </p>
            </div>

            {deactivatedCount > 0 && (
              <label className="checkbox-filter">
                <input
                  type="checkbox"
                  checked={showDeactivated}
                  onChange={(event) => setShowDeactivated(event.target.checked)}
                />
                Show deactivated ({deactivatedCount})
              </label>
            )}
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
              visibleUsers.map((user) => (
                <MemberRow
                  key={user.id}
                  user={user}
                  activeCount={workload[user.id] ?? 0}
                  admin={admin}
                  isYou={user.id === currentUser?.id}
                  canEmailReset={canEmailReset}
                  onUpdated={handleMemberUpdated}
                />
              ))}
          </div>
        </section>

        <BusinessHoursSettings admin={admin} />
      </main>
    </div>
  )
}

export default Team
