import { useState } from 'react'
import { countTicks, shortDate } from '../../utils/reports'

const WIDTH = 720
const HEIGHT = 220
const PAD = { top: 12, right: 16, bottom: 28, left: 36 }

const SERIES = [
  { key: 'reported', label: 'Reported', className: 'series-1' },
  { key: 'resolved', label: 'Resolved', className: 'series-2' },
]

/**
 * Incidents reported and resolved per day: two lines, a crosshair with a
 * tooltip for every series at the hovered day (mouse or arrow keys), and a
 * table view with the same numbers.
 */
function DailyChart({ days }) {
  const [active, setActive] = useState(null)
  const [showTable, setShowTable] = useState(false)

  const { max, ticks } = countTicks(Math.max(0, ...days.flatMap((d) => [d.reported, d.resolved])))
  const plotWidth = WIDTH - PAD.left - PAD.right
  const plotHeight = HEIGHT - PAD.top - PAD.bottom
  const x = (index) => PAD.left + (days.length === 1 ? plotWidth / 2 : (index / (days.length - 1)) * plotWidth)
  const y = (value) => PAD.top + plotHeight - (value / max) * plotHeight

  const path = (key) =>
    days.map((day, index) => `${index === 0 ? 'M' : 'L'}${x(index).toFixed(1)},${y(day[key]).toFixed(1)}`).join(' ')

  const labelIndexes = [...new Set([0, Math.floor((days.length - 1) / 2), days.length - 1])]

  function indexAt(event) {
    const box = event.currentTarget.getBoundingClientRect()
    const svgX = ((event.clientX - box.left) / box.width) * WIDTH
    const ratio = (svgX - PAD.left) / plotWidth
    return Math.min(days.length - 1, Math.max(0, Math.round(ratio * (days.length - 1))))
  }

  function handleKey(event) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
    event.preventDefault()
    const step = event.key === 'ArrowLeft' ? -1 : 1
    setActive((current) =>
      Math.min(days.length - 1, Math.max(0, (current ?? (step > 0 ? -1 : days.length)) + step))
    )
  }

  const activeDay = active == null ? null : days[active]

  return (
    <figure className="report-chart">
      <div className="chart-legend" aria-hidden="true">
        {SERIES.map((series) => (
          <span key={series.key}>
            <i className={`legend-line ${series.className}`} />
            {series.label}
          </span>
        ))}
      </div>

      <div className="chart-frame">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          role="img"
          aria-label="Incidents reported and resolved per day. Use the table view for exact numbers."
          tabIndex={0}
          onPointerMove={(event) => setActive(indexAt(event))}
          onPointerLeave={() => setActive(null)}
          onKeyDown={handleKey}
          onBlur={() => setActive(null)}
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line className="chart-grid" x1={PAD.left} x2={WIDTH - PAD.right} y1={y(tick)} y2={y(tick)} />
              <text className="chart-tick" x={PAD.left - 8} y={y(tick)} dy="0.32em" textAnchor="end">
                {tick}
              </text>
            </g>
          ))}
          {labelIndexes.map((index) => (
            <text
              key={index}
              className="chart-tick"
              x={x(index)}
              y={HEIGHT - 8}
              textAnchor={index === 0 ? 'start' : index === days.length - 1 ? 'end' : 'middle'}
            >
              {shortDate(days[index].date)}
            </text>
          ))}

          {activeDay && (
            <line className="chart-crosshair" x1={x(active)} x2={x(active)} y1={PAD.top} y2={PAD.top + plotHeight} />
          )}

          {SERIES.map((series) => (
            <path key={series.key} className={`chart-line ${series.className}`} d={path(series.key)} />
          ))}

          {activeDay &&
            SERIES.map((series) => (
              <circle
                key={series.key}
                className={`chart-dot ${series.className}`}
                cx={x(active)}
                cy={y(activeDay[series.key])}
                r="4"
              />
            ))}
        </svg>

        {activeDay && (
          <div
            className="chart-tooltip"
            role="status"
            style={{ left: `${(x(active) / WIDTH) * 100}%` }}
          >
            <strong>{shortDate(activeDay.date)}</strong>
            {SERIES.map((series) => (
              <span key={series.key}>
                <i className={`legend-line ${series.className}`} />
                <b>{activeDay[series.key]}</b> {series.label.toLowerCase()}
              </span>
            ))}
          </div>
        )}
      </div>

      <button type="button" className="link-button" onClick={() => setShowTable((value) => !value)}>
        {showTable ? 'Hide table' : 'Show as table'}
      </button>
      {showTable && (
        <div className="report-table-wrap">
          <table className="report-table">
            <thead>
              <tr>
                <th scope="col">Day</th>
                <th scope="col">Reported</th>
                <th scope="col">Resolved</th>
              </tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr key={day.date}>
                  <th scope="row">{shortDate(day.date)}</th>
                  <td>{day.reported}</td>
                  <td>{day.resolved}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </figure>
  )
}

export default DailyChart
