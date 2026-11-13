import { NavLink } from 'react-router-dom'
import { useCreateIncident } from '../context/createIncidentContext'
import { useCurrentUser } from '../context/currentUserContext'
import { initials } from '../utils/initials'
import NotificationBell from './NotificationBell'

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
  const { currentUser, signOut } = useCurrentUser()

  return (
    <aside className="sidebar">
      <div className="sidebar-top">
        <div className="brand">
          <span className="brand-mark">IQ</span>

          <div>
            <strong>IncidentIQ</strong>
            <small>Intelligent Operations</small>
          </div>
        </div>

        <NotificationBell />
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

        <NavLink
          to="/account"
          className="signed-in-as"
          title="Your account and password"
        >
          <strong>{currentUser?.displayName}</strong>
          {currentUser && <small>{ROLE_LABELS[currentUser.role]}</small>}
        </NavLink>

        <button
          type="button"
          className="sign-out-button"
          onClick={signOut}
          title="Sign out"
        >
          Sign out
        </button>
      </div>
    </aside>
  )
}

export default Sidebar
