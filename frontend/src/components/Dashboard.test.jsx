import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyWork from './MyWork'
import MyIncidents from './MyIncidents'
import OpenTrend from './OpenTrend'
import {
  getMyIncidents,
  getMyWork,
  getOpenTrend,
  updateIncidentAssignee,
} from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getMyWork: vi.fn(),
  getMyIncidents: vi.fn(),
  getOpenTrend: vi.fn(),
  updateIncidentAssignee: vi.fn(),
}))

const future = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString()

function incident(number, title, extra = {}) {
  return {
    id: `inc-${number}`,
    incidentNumber: number,
    title,
    priority: 'HIGH',
    status: 'IN_PROGRESS',
    createdAt: '2026-01-05T09:00:00Z',
    sla: {
      response: { dueAt: future, completedAt: '2026-01-05T09:10:00Z', state: 'MET' },
      resolution: { dueAt: future, completedAt: null, state: 'ON_TRACK' },
    },
    ...extra,
  }
}

const WORK = {
  dueSoon: [incident(3, 'Email down'), incident(1, 'Printer jam', { priority: 'LOW' })],
  waiting: [
    incident(4, 'VPN drops', {
      status: 'ON_HOLD',
      holdReason: 'WAITING_ON_REPORTER',
      onHoldSince: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(),
    }),
  ],
  unassigned: [incident(5, 'Laptop slow', { status: 'OPEN', priority: 'MEDIUM' })],
  unassignedTotal: 7,
  unread: [
    {
      incidentId: 'inc-3',
      incidentNumber: 3,
      title: 'Email down',
      type: 'MENTIONED',
      message: 'Sam mentioned you on INC-003',
      createdAt: '2026-01-05T09:00:00Z',
    },
  ],
}

beforeEach(() => {
  getMyWork.mockReset().mockResolvedValue(WORK)
  getMyIncidents.mockReset()
  getOpenTrend.mockReset()
  updateIncidentAssignee.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('MyWork', () => {
  it('shows what is due, waiting, unassigned and unread', async () => {
    renderPage(<MyWork currentUser={USERS.agent} />)

    const due = await screen.findByRole('region', { name: 'Due soonest' })
    expect(within(due).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'Email downINC-003',
      'Printer jamINC-001',
    ])
    expect(within(due).getAllByText(/left/).length).toBeGreaterThan(0)

    const waiting = screen.getByRole('region', { name: 'Waiting on someone' })
    expect(within(waiting).getByText(/Waiting on reporter · 3h/)).toBeInTheDocument()

    const queue = screen.getByRole('region', { name: 'Unassigned' })
    expect(within(queue).getByText('7')).toBeInTheDocument()
    expect(within(queue).getByRole('link', { name: '6 more on the incident list' })).toBeInTheDocument()

    const unread = screen.getByRole('region', { name: 'Unread updates' })
    expect(within(unread).getByText('Sam mentioned you on INC-003')).toBeInTheDocument()
  })

  it('takes an unassigned incident and reloads', async () => {
    updateIncidentAssignee.mockResolvedValue({})
    const user = userEvent.setup()
    renderPage(<MyWork currentUser={USERS.agent} />)

    await user.click(await screen.findByRole('button', { name: 'Take INC-005' }))

    expect(updateIncidentAssignee).toHaveBeenCalledWith('inc-5', 'u-agent')
    await waitFor(() => expect(getMyWork).toHaveBeenCalledTimes(2))
  })

  it('says when there is nothing to do', async () => {
    getMyWork.mockResolvedValue({ dueSoon: [], waiting: [], unassigned: [], unassignedTotal: 0, unread: [] })
    renderPage(<MyWork currentUser={USERS.agent} />)

    expect(await screen.findByText('Nothing open is assigned to you.')).toBeInTheDocument()
    expect(screen.getByText("You're all caught up.")).toBeInTheDocument()
    expect(screen.getByText('Every active incident has someone on it.')).toBeInTheDocument()
  })
})

describe('MyIncidents', () => {
  it("lists a reporter's incidents and flags the ones waiting on them", async () => {
    getMyIncidents.mockResolvedValue([
      incident(4, 'VPN drops', { status: 'ON_HOLD', holdReason: 'WAITING_ON_REPORTER' }),
      incident(2, 'Printer jam', { status: 'RESOLVED' }),
    ])
    renderPage(<MyIncidents />, { currentUser: USERS.user })

    expect(await screen.findByRole('link', { name: /VPN drops/ })).toBeInTheDocument()
    expect(screen.getByText('Resolved')).toBeInTheDocument()
    expect(screen.getAllByText(/Waiting on you/)).toHaveLength(1)
  })

  it('has a friendly empty state', async () => {
    getMyIncidents.mockResolvedValue([])
    renderPage(<MyIncidents />, { currentUser: USERS.user })

    expect(await screen.findByText("You haven't reported any incidents yet.")).toBeInTheDocument()
  })
})

describe('OpenTrend', () => {
  it('draws one line with a table view and no legend', async () => {
    getOpenTrend.mockResolvedValue([
      { date: '2026-01-04', open: 3 },
      { date: '2026-01-05', open: 5 },
    ])
    const user = userEvent.setup()
    renderPage(<OpenTrend />)

    expect(await screen.findByText(/5 open now/)).toBeInTheDocument()
    expect(getOpenTrend).toHaveBeenCalledWith(30)
    expect(screen.getByRole('img', { name: /Open incidents at the end of each day/ })).toBeInTheDocument()
    expect(document.querySelector('.chart-legend')).toBeNull()

    await user.click(screen.getByRole('button', { name: 'Show as table' }))
    const table = screen.getByRole('table')
    expect(within(table).getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual(['Day', 'Open'])
    expect(within(table).getByText('5')).toBeInTheDocument()
  })
})
