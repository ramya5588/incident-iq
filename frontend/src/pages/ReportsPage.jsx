import { useEffect, useState } from 'react'
import Sidebar from '../components/Sidebar'
import DailyChart from '../components/reports/DailyChart'
import SlaBars from '../components/reports/SlaBars'
import { useCurrentUser } from '../context/currentUserContext'
import { describeApiError, getReportSummary, reportCsvUrl } from '../services/incidentApi'
import { canWorkIncidents } from '../utils/roles'
import { formatDuration } from '../utils/sla'
import { RANGE_PRESETS, browserTimeZone, lastDays, metRate } from '../utils/reports'

const PRIORITY_OPTIONS = [
  { value: '', label: 'All priorities' },
  { value: 'CRITICAL', label: 'Critical' },
  { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW', label: 'Low' },
]

function Stat({ label, value, hint }) {
  return (
    <article className="stat-card report-stat">
      <span>{label}</span>
      <strong>{value}</strong>
      {hint && <small>{hint}</small>}
    </article>
  )
}

function seconds(value) {
  return value == null ? '—' : formatDuration(value * 1000)
}

function rate(outcome) {
  const value = metRate(outcome)
  return value == null ? '—' : `${value}%`
}

/** SLA performance, volume, categories and workload over a date range. */
function ReportsPage() {
  const { currentUser } = useCurrentUser()
  const [preset, setPreset] = useState('30')
  const [range, setRange] = useState(() => lastDays(30))
  const [priority, setPriority] = useState('')
  // The last answer and which request it was for; a newer request means loading.
  const [result, setResult] = useState({ key: null, report: null, error: '' })
  const timeZone = browserTimeZone()
  const allowed = canWorkIncidents(currentUser)
  const key = JSON.stringify({ ...range, timeZone, priority })
  const loading = allowed && result.key !== key
  const report = result.report
  const error = result.key === key ? result.error : ''

  useEffect(() => {
    if (!allowed || !range.from || !range.to) return
    let cancelled = false
    getReportSummary({ ...range, timeZone, priority })
      .then((data) => !cancelled && setResult({ key, report: data, error: '' }))
      .catch((requestError) => {
        console.error(requestError)
        if (!cancelled) {
          setResult((current) => ({
            key,
            report: current.report,
            error: describeApiError(requestError, 'Unable to load the report.'),
          }))
        }
      })
    return () => {
      cancelled = true
    }
  }, [allowed, range, timeZone, priority, key])

  function choosePreset(value) {
    setPreset(value)
    const choice = RANGE_PRESETS.find((option) => option.value === value)
    if (choice?.days) setRange(lastDays(choice.days))
  }

  if (!allowed) {
    return (
      <div className="app-shell">
        <Sidebar />
        <main className="main-content">
          <p>Reports are for agents and admins.</p>
        </main>
      </div>
    )
  }

  return (
    <div className="app-shell">
      <Sidebar />

      <main className="main-content">
        <header className="page-header">
          <div>
            <p className="eyebrow">REPORTS</p>
            <h1>Reports</h1>
            <p>SLA performance, volume and workload. Days are in your time zone ({timeZone}).</p>
          </div>
        </header>

        <section className="report-filters" aria-label="Report filters">
          <label>
            Period
            <select value={preset} onChange={(event) => choosePreset(event.target.value)}>
              {RANGE_PRESETS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          {preset === 'custom' && (
            <>
              <label>
                From
                <input
                  type="date"
                  value={range.from}
                  max={range.to}
                  onChange={(event) => setRange((current) => ({ ...current, from: event.target.value }))}
                />
              </label>
              <label>
                To
                <input
                  type="date"
                  value={range.to}
                  min={range.from}
                  onChange={(event) => setRange((current) => ({ ...current, to: event.target.value }))}
                />
              </label>
            </>
          )}
          <label>
            Priority
            <select value={priority} onChange={(event) => setPriority(event.target.value)}>
              {PRIORITY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <a className="secondary-button report-download" href={reportCsvUrl({ ...range, timeZone, priority })}>
            Download CSV
          </a>
        </section>

        {error && <p className="error-message">{error}</p>}
        {loading && !report && <p className="timeline-empty">Loading report…</p>}

        {report && (
          <div className={loading ? 'report-body refreshing' : 'report-body'} aria-busy={loading}>
            <section className="report-stats">
              <Stat label="Reported" value={report.reported} />
              <Stat label="Resolved" value={report.resolved} />
              <Stat
                label="Median first response"
                value={seconds(report.medianResponseSeconds)}
                hint="Reported in this period"
              />
              <Stat
                label="Median time to resolve"
                value={seconds(report.medianResolveSeconds)}
                hint="Resolved in this period"
              />
              <Stat
                label="Response SLA met"
                value={rate(report.responseSla)}
                hint={`${report.responseSla.met} met · ${report.responseSla.missed} missed`}
              />
              <Stat
                label="Resolution SLA met"
                value={rate(report.resolutionSla)}
                hint={`${report.resolutionSla.met} met · ${report.resolutionSla.missed} missed`}
              />
              <Stat label="Overdue now" value={report.overdueNow} hint="Open or in progress" />
              <Stat
                label="Satisfaction"
                value={
                  report.satisfaction.average == null
                    ? '—'
                    : `${report.satisfaction.average.toFixed(1)} / 5`
                }
                hint={
                  report.satisfaction.ratings === 0
                    ? 'No ratings in this period'
                    : `${report.satisfaction.ratings} rating${
                        report.satisfaction.ratings === 1 ? '' : 's'
                      } · ${Math.round(
                        (report.satisfaction.satisfied / report.satisfaction.ratings) * 100
                      )}% rated 4 or 5`
                }
              />
            </section>

            <section className="content-card report-card">
              <h2>Reported and resolved per day</h2>
              <DailyChart days={report.daily} />
            </section>

            <section className="content-card report-card">
              <h2>SLA by priority</h2>
              <p className="report-note">
                Incidents reported in this period whose deadline is decided. Ones still
                running, or on hold, count once they're done or missed.
              </p>
              <div className="sla-bars-grid">
                <SlaBars title="Response" rows={report.byPriority} outcome="response" />
                <SlaBars title="Resolution" rows={report.byPriority} outcome="resolution" />
              </div>
            </section>

            <div className="report-columns">
              <section className="content-card report-card">
                <h2>Top categories</h2>
                {report.topCategories.length === 0 ? (
                  <p className="report-note">Nothing reported in this period.</p>
                ) : (
                  <ol className="category-bars">
                    {report.topCategories.map((row) => (
                      <li key={row.category ?? '(none)'}>
                        <span className="category-name">{row.category ?? 'No category'}</span>
                        <span className="category-track" aria-hidden="true">
                          <span
                            className="category-fill series-1"
                            style={{ width: `${(row.count / report.topCategories[0].count) * 100}%` }}
                          />
                        </span>
                        <span className="category-count">{row.count}</span>
                      </li>
                    ))}
                  </ol>
                )}
              </section>

              <section className="content-card report-card">
                <h2>Open work now</h2>
                <div className="report-table-wrap">
                  <table className="report-table">
                    <thead>
                      <tr>
                        <th scope="col">Assignee</th>
                        <th scope="col">Open</th>
                        <th scope="col">In progress</th>
                        <th scope="col">On hold</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.workload.assignees.map((row) => (
                        <tr key={row.userId}>
                          <th scope="row">{row.name}</th>
                          <td>{row.open}</td>
                          <td>{row.inProgress}</td>
                          <td>{row.onHold}</td>
                        </tr>
                      ))}
                      <tr>
                        <th scope="row">Unassigned</th>
                        <td colSpan={3}>{report.workload.unassigned}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}

export default ReportsPage
