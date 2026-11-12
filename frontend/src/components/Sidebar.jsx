import { NavLink } from 'react-router-dom'
import { useCreateIncident } from '../context/createIncidentContext'
import { useCurrentUser } from '../context/currentUserContext'
import { initials } from '../utils/initials'

const ROLE_LABELS = {
  ADMIN: 'Administrator',
  AGENT: 'Support agent',
  USER: 'User',
}

function navClass({ isActive }) {
  return isActive ? 'nav-item active' : 'nav-item'
}

function Sidebar() {
  const openCreateIncident = useCreateIncident()
  const { users, usersError, currentUser, setCurrentUserId } = useCurrentUser()

  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand-mark">IQ</span>

        <div>
          <strong>IncidentIQ</strong>
          <small>Intelligent Operations</small>
        </div>
      </div>

      <nav>
        <NavLink className={navClass} to="/" end>
          Dashboard
        </NavLink>
        <NavLink className={navClass} to="/incidents" end>
          Incidents
        </NavLink>
        <button
          type="button"
          className="nav-item"
          onClick={openCreateIncident}
        >
          Create incident
        </button>
        <NavLink className={navClass} to="/team" end>
          Team
        </NavLink>
      </nav>

      <div className="sidebar-footer">
        <span className="avatar">
          {currentUser ? initials(currentUser.displayName) : '?'}
        </span>

        <div className="acting-as">
          <label htmlFor="acting-as-select">Acting as</label>
          <select
            id="acting-as-select"
            value={currentUser?.id ?? ''}
            onChange={(event) => setCurrentUserId(event.target.value)}
            disabled={users.length === 0}
            title={usersError || undefined}
          >
            {users.length === 0 && (
              <option value="">{usersError ? 'Unavailable' : 'Loading…'}</option>
            )}
            {users.map((user) => (
              <option key={user.id} value={user.id}>
                {user.displayName}
              </option>
            ))}
          </select>
          {currentUser && <small>{ROLE_LABELS[currentUser.role]}</small>}
        </div>
      </div>
    </aside>
  )
}

export default Sidebar
