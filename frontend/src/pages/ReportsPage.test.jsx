import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ReportsPage from './ReportsPage'
import { getReportSummary } from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'
import { countTicks, lastDays, metRate } from '../utils/reports'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getReportSummary: vi.fn(),
  getUnreadNotificationCount: vi.fn().mockResolvedValue(0),
}))

const outcome = (met, missed) => ({ met, missed })

const REPORT = {
  from: '2026-01-05',
  to: '2026-01-07',
  timeZone: 'UTC',
  priority: null,
  reported: 4,
  resolved: 3,
  medianResponseSeconds: 1800,
  medianResolveSeconds: 10800,
  responseSla: outcome(2, 2),
  resolutionSla: outcome(1, 1),
  overdueNow: 1,
  daily: [
    { date: '2026-01-05', reported: 2, resolved: 3 },
    { date: '2026-01-06', reported: 1, resolved: 0 },
    { date: '2026-01-07', reported: 1, resolved: 0 },
  ],
  byPriority: [
    { priority: 'LOW', reported: 1, response: outcome(0, 1), resolution: outcome(0, 0) },
    { priority: 'MEDIUM', reported: 0, response: outcome(0, 0), resolution: outcome(0, 0) },
    { priority: 'HIGH', reported: 3, response: outcome(2, 1), resolution: outcome(1, 1) },
    { priority: 'CRITICAL', reported: 0, response: outcome(0, 0), resolution: outcome(0, 0) },
  ],
  topCategories: [
    { category: 'Hardware', count: 2 },
    { category: null, count: 1 },
  ],
  workload: {
    assignees: [{ userId: 'u-agent', name: 'Alex Agent', open: 0, inProgress: 1, onHold: 1 }],
    unassigned: 1,
  },
}

describe('report helpers', () => {
  it('works out ranges, ticks and rates', () => {
    expect(lastDays(7, new Date(2026, 0, 7))).toEqual({ from: '2026-01-01', to: '2026-01-07' })
    expect(lastDays(1, new Date(2026, 0, 7))).toEqual({ from: '2026-01-07', to: '2026-01-07' })
    expect(countTicks(0)).toEqual({ max: 1, ticks: [0, 1] })
    expect(countTicks(3).ticks).toEqual([0, 1, 2, 3])
    expect(countTicks(37)).toEqual({ max: 40, ticks: [0, 10, 20, 30, 40] })
    expect(metRate(outcome(3, 1))).toBe(75)
    expect(metRate(outcome(0, 0))).toBeNull()
  })
})

describe('ReportsPage', () => {
  beforeEach(() => {
    getReportSummary.mockReset().mockResolvedValue(REPORT)
  })

  it('shows the headline numbers, charts and workload', async () => {
    renderPage(<ReportsPage />, { currentUser: USERS.agent })
    const main = within(await screen.findByRole('main'))

    expect(await main.findByText('Median first response')).toBeInTheDocument()
    const stat = (label) =>
      main.getByText(label, { selector: '.report-stat > span' }).closest('.report-stat')
    expect(within(stat('Reported')).getByText('4')).toBeInTheDocument()
    expect(within(stat('Median first response')).getByText('30m')).toBeInTheDocument()
    expect(within(stat('Median time to resolve')).getByText('3h')).toBeInTheDocument()
    expect(within(stat('Response SLA met')).getByText('50%')).toBeInTheDocument()
    expect(within(stat('Overdue now')).getByText('1')).toBeInTheDocument()

    // SLA bars carry their numbers in text, not only colour.
    expect(main.getAllByText('2 met · 1 missed (67%)')).toHaveLength(1)
    expect(main.getAllByText('None decided yet').length).toBeGreaterThan(0)

    expect(main.getByText('No category')).toBeInTheDocument()
    expect(main.getByRole('row', { name: /Alex Agent/ })).toHaveTextContent('Alex Agent011')
  })

  it('asks for the last 30 days in the viewer’s time zone, then follows the filters', async () => {
    const user = userEvent.setup()
    renderPage(<ReportsPage />, { currentUser: USERS.agent })
    await screen.findByText('Median first response')

    const first = getReportSummary.mock.calls[0][0]
    expect(first).toEqual({ ...lastDays(30), timeZone: expect.any(String), priority: '' })

    await user.selectOptions(screen.getByLabelText('Priority'), 'HIGH')
    expect(getReportSummary).toHaveBeenLastCalledWith(expect.objectContaining({ priority: 'HIGH' }))

    await user.selectOptions(screen.getByLabelText('Period'), '7')
    expect(getReportSummary).toHaveBeenLastCalledWith(expect.objectContaining(lastDays(7)))

    const download = screen.getByRole('link', { name: 'Download CSV' })
    expect(download.getAttribute('href')).toMatch(
      /^\/api\/reports\/incidents\.csv\?from=\d{4}-\d\d-\d\d&to=\d{4}-\d\d-\d\d&timeZone=.+&priority=HIGH$/
    )
  })

  it('offers custom dates', async () => {
    const user = userEvent.setup()
    renderPage(<ReportsPage />, { currentUser: USERS.agent })
    await screen.findByText('Median first response')

    await user.selectOptions(screen.getByLabelText('Period'), 'custom')
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-01-01' } })

    expect(getReportSummary).toHaveBeenLastCalledWith(
      expect.objectContaining({ from: '2026-01-01' })
    )
  })

  it('shows the daily numbers on hover, by keyboard and as a table', async () => {
    const user = userEvent.setup()
    renderPage(<ReportsPage />, { currentUser: USERS.agent })
    const chart = await screen.findByRole('img', { name: /reported and resolved per day/ })

    chart.focus()
    await user.keyboard('{ArrowRight}')
    const tooltip = within(chart.closest('.chart-frame')).getByRole('status')
    expect(tooltip).toHaveTextContent('2 reported')
    expect(tooltip).toHaveTextContent('3 resolved')

    await user.click(screen.getByRole('button', { name: 'Show as table' }))
    const table = screen.getAllByRole('table')[0]
    expect(within(table).getAllByRole('row')).toHaveLength(4)
  })

  it('explains a refused request', async () => {
    getReportSummary.mockRejectedValue(
      Object.assign(new Error('Request failed'), {
        response: { status: 400, data: { message: 'Reports cover at most 366 days.' } },
      })
    )
    vi.spyOn(console, 'error').mockImplementation(() => {})
    renderPage(<ReportsPage />, { currentUser: USERS.admin })

    expect(await screen.findByText('Reports cover at most 366 days.')).toBeInTheDocument()
  })

  it('is only for agents and admins', async () => {
    renderPage(<ReportsPage />, { currentUser: USERS.user })

    expect(await screen.findByText('Reports are for agents and admins.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Reports' })).not.toBeInTheDocument()
    expect(getReportSummary).not.toHaveBeenCalled()
  })
})
