import { NavLink } from 'react-router-dom'

function navClass({ isActive }) {
  return isActive ? 'nav-item active' : 'nav-item'
}

function Sidebar() {
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
  )
}

export default Sidebar
