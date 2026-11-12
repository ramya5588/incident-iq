import { NavLink } from 'react-router-dom'
import { useCreateIncident } from '../context/createIncidentContext'

function navClass({ isActive }) {
  return isActive ? 'nav-item active' : 'nav-item'
}

function Sidebar() {
  const openCreateIncident = useCreateIncident()

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
        <span className="avatar">RN</span>

        <div>
          <strong>Ramya Navuluri</strong>
          <small>Administrator</small>
        </div>
      </div>
    </aside>
  )
}

export default Sidebar
