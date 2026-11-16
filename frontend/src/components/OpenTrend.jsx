import { useEffect, useState } from 'react'
import { getOpenTrend } from '../services/incidentApi'
import DailyChart from './reports/DailyChart'

const DAYS = 30
const SERIES = [{ key: 'open', label: 'Open', className: 'series-1' }]

/** Open incidents at the end of each of the last 30 days. */
function OpenTrend() {
  const [days, setDays] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    getOpenTrend(DAYS)
      .then((data) => {
        if (!cancelled) setDays(data)
      })
      .catch((requestError) => {
        console.error(requestError)
        if (!cancelled) setError('Unable to load the trend.')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const today = days?.[days.length - 1]?.open

  return (
    <section className="content-card report-card" aria-labelledby="open-trend-title">
      <div className="section-heading">
        <div>
          <h2 id="open-trend-title">Open incidents, last {DAYS} days</h2>
          <p>
            How many were still open at the end of each day
            {today != null ? `. ${today} open now.` : '.'}
          </p>
        </div>
      </div>
      {error && <p className="error-message">{error}</p>}
      {!days && !error && <p className="timeline-empty">Loading…</p>}
      {days && (
        <DailyChart
          days={days}
          series={SERIES}
          label="Open incidents at the end of each day over the last 30 days. Use the table view for exact numbers."
        />
      )}
    </section>
  )
}

export default OpenTrend
