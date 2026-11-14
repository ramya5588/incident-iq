import { metRate } from '../../utils/reports'

const PRIORITY_LABELS = { CRITICAL: 'Critical', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' }

/**
 * Met vs missed per priority for one kind of deadline. Each row is a bar of
 * met (blue) then missed (orange) with the counts in text beside it, so the
 * colours are never the only way to read it.
 */
function SlaBars({ title, rows, outcome }) {
  const widest = Math.max(1, ...rows.map((row) => row[outcome].met + row[outcome].missed))
  // Highest priority first.
  const ordered = [...rows].reverse()

  return (
    <figure className="report-chart sla-bars">
      <figcaption>{title}</figcaption>
      <div className="chart-legend" aria-hidden="true">
        <span>
          <i className="legend-swatch series-1" />✓ Met
        </span>
        <span>
          <i className="legend-swatch series-2" />✕ Missed
        </span>
      </div>
      <dl>
        {ordered.map((row) => {
          const { met, missed } = row[outcome]
          const rate = metRate(row[outcome])
          return (
            <div className="sla-bar-row" key={row.priority}>
              <dt>{PRIORITY_LABELS[row.priority]}</dt>
              <dd>
                {met + missed > 0 && (
                <span className="sla-bar-track" aria-hidden="true">
                  {met > 0 && (
                    <span
                      className="sla-bar-fill series-1"
                      style={{ width: `${(met / widest) * 100}%` }}
                      title={`${met} met`}
                    />
                  )}
                  {missed > 0 && (
                    <span
                      className="sla-bar-fill series-2"
                      style={{ width: `${(missed / widest) * 100}%` }}
                      title={`${missed} missed`}
                    />
                  )}
                </span>
                )}
                <span className="sla-bar-text">
                  {met + missed === 0
                    ? 'None decided yet'
                    : `${met} met · ${missed} missed (${rate}%)`}
                </span>
              </dd>
            </div>
          )
        })}
      </dl>
    </figure>
  )
}

export default SlaBars
