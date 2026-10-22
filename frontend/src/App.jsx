import './App.css'

const incidents = [
  {
    number: 'INC-001',
    title: 'Users cannot sign in after deployment',
    category: 'Authentication',
    priority: 'HIGH',
    status: 'OPEN',
  },
  {
    number: 'INC-002',
    title: 'Payment service response time is elevated',
    category: 'Performance',
    priority: 'CRITICAL',
    status: 'IN_PROGRESS',
  },
  {
    number: 'INC-003',
    title: 'Email notifications are delayed',
    category: 'Messaging',
    priority: 'MEDIUM',
    status: 'RESOLVED',
  },
]

function App() {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">IQ</span>
          <div>
            <strong>IncidentIQ</strong>
            <small>Intelligent Operations</small>
          </div>
        </div>

        <nav>
          <button className="nav-item active">Dashboard</button>
          <button className="nav-item">Incidents</button>
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

      <main className="main-content">
        <header className="page-header">
          <div>
            <p className="eyebrow">OPERATIONS OVERVIEW</p>
            <h1>Incident dashboard</h1>
            <p>Monitor incidents and accelerate resolution with AI triage.</p>
          </div>

          <button className="primary-button">+ New incident</button>
        </header>

        <section className="stats-grid">
          <article className="stat-card">
            <span>Open incidents</span>
            <strong>12</strong>
            <small>3 require attention</small>
          </article>

          <article className="stat-card">
            <span>In progress</span>
            <strong>7</strong>
            <small>Across 4 agents</small>
          </article>

          <article className="stat-card">
            <span>Resolved today</span>
            <strong>18</strong>
            <small>12% above average</small>
          </article>

          <article className="stat-card accent-card">
            <span>AI triaged</span>
            <strong>94%</strong>
            <small>of incoming incidents</small>
          </article>
        </section>

        <section className="content-card">
          <div className="section-heading">
            <div>
              <h2>Recent incidents</h2>
              <p>Latest incidents across your support environment</p>
            </div>
            <button className="secondary-button">View all</button>
          </div>

          <div className="incident-table">
            <div className="table-row table-header">
              <span>Incident</span>
              <span>Category</span>
              <span>Priority</span>
              <span>Status</span>
            </div>

            {incidents.map((incident) => (
              <div className="table-row" key={incident.number}>
                <div>
                  <strong>{incident.title}</strong>
                  <small>{incident.number}</small>
                </div>
                <span>{incident.category}</span>
                <span className={`badge ${incident.priority.toLowerCase()}`}>
                  {incident.priority}
                </span>
                <span className="status">
                  {incident.status.replace('_', ' ')}
                </span>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  )
}

export default App