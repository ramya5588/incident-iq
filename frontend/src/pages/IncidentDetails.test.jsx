import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IncidentDetails from './IncidentDetails'
import {
  addIncidentComment,
  getIncidentById,
  getIncidentEvents,
  updateIncidentStatus,
} from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getIncidentById: vi.fn(),
  getIncidentEvents: vi.fn(),
  updateIncidentStatus: vi.fn(),
  addIncidentComment: vi.fn(),
  listAttachments: vi.fn().mockResolvedValue([]),
  getWatchers: vi.fn().mockResolvedValue({ watching: false, count: 0, watchers: [] }),
  getDuplicates: vi.fn().mockResolvedValue([]),
}))

const INCIDENT = {
  id: 'inc-1',
  incidentNumber: 7,
  title: 'VPN drops every hour',
  description: 'Remote staff are disconnected.',
  status: 'OPEN',
  priority: 'HIGH',
  category: 'Network',
  assignedToId: null,
  assignedToName: null,
  createdAt: '2026-01-05T09:00:00Z',
  sla: {
    response: { dueAt: '2026-01-05T10:00:00Z', completedAt: null, state: 'BREACHED' },
    resolution: { dueAt: '2026-01-05T17:00:00Z', completedAt: null, state: 'BREACHED' },
  },
}

function renderAs(currentUser) {
  return renderPage(<IncidentDetails />, {
    currentUser,
    route: '/incidents/inc-1',
    path: '/incidents/:id',
  })
}

describe('IncidentDetails', () => {
  beforeEach(() => {
    getIncidentById.mockResolvedValue(INCIDENT)
    getIncidentEvents.mockResolvedValue([])
  })

  it('lets agents work the incident', async () => {
    renderAs(USERS.agent)

    expect(await screen.findByRole('heading', { name: INCIDENT.title })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit details' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Status:/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Update status' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Run AI triage' })).toBeInTheDocument()

    // Only agents and admins are offered as assignees.
    const options = [...screen.getByLabelText(/Assigned to:/).options].map((o) => o.text)
    expect(options).toEqual(['Unassigned', 'Ada Admin (admin)', 'Alex Agent (agent)'])
  })

  it('shows reporters a read-only view they can still comment on', async () => {
    renderAs(USERS.user)

    expect(await screen.findByRole('heading', { name: INCIDENT.title })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit details' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Update status' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Run AI triage' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Assigned to:/)).not.toBeInTheDocument()

    expect(screen.getByText('Unassigned')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Comment' })).toBeInTheDocument()
  })

  it('shows both SLA deadlines', async () => {
    renderAs(USERS.agent)

    expect(await screen.findByRole('heading', { name: 'SLA' })).toBeInTheDocument()
    expect(screen.getByText('Respond by')).toBeInTheDocument()
    expect(screen.getByText('Resolve by')).toBeInTheDocument()
    expect(screen.getAllByText(/^Overdue /)).toHaveLength(2)
  })
})

describe('IncidentDetails on hold', () => {
  const ON_HOLD = {
    ...INCIDENT,
    status: 'ON_HOLD',
    assignedToId: 'u-agent',
    assignedToName: 'Alex Agent',
    holdReason: 'WAITING_ON_REPORTER',
    holdNote: 'Asked for a screenshot',
    onHoldSince: '2026-01-05T11:00:00Z',
    sla: {
      response: { dueAt: '2026-01-05T10:00:00Z', completedAt: '2026-01-05T09:30:00Z', state: 'MET' },
      resolution: { dueAt: '2026-01-06T17:00:00Z', completedAt: null, state: 'PAUSED' },
    },
  }

  beforeEach(() => {
    getIncidentById.mockReset().mockResolvedValue(INCIDENT)
    getIncidentEvents.mockReset().mockResolvedValue([])
    updateIncidentStatus.mockReset()
    addIncidentComment.mockReset()
  })

  it('asks why before putting an incident on hold', async () => {
    updateIncidentStatus.mockResolvedValue(ON_HOLD)
    const user = userEvent.setup()
    renderAs(USERS.agent)

    await user.selectOptions(await screen.findByLabelText(/Status:/), 'ON_HOLD')
    const update = screen.getByRole('button', { name: 'Update status' })
    expect(update).toBeDisabled()

    await user.selectOptions(screen.getByLabelText('Waiting for'), 'WAITING_ON_REPORTER')
    expect(screen.getByText(/reporter's next comment puts it back in progress/)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Note (optional)'), 'Asked for a screenshot')
    await user.click(update)

    expect(updateIncidentStatus).toHaveBeenCalledWith('inc-1', 'ON_HOLD', {
      holdReason: 'WAITING_ON_REPORTER',
      holdNote: 'Asked for a screenshot',
    })
    expect(
      await screen.findByText('On hold. The resolution clock is paused.')
    ).toBeInTheDocument()
  })

  it('shows the hold and a paused resolution clock', async () => {
    getIncidentById.mockResolvedValue(ON_HOLD)
    renderAs(USERS.user)

    expect(await screen.findByText(/Waiting on reporter\. Asked for a screenshot/)).toBeInTheDocument()
    expect(screen.getByText('Paused')).toBeInTheDocument()
    expect(screen.getByText('Moves later while on hold')).toBeInTheDocument()
  })

  it("reloads after the reporter's reply takes it off hold", async () => {
    getIncidentById
      .mockResolvedValueOnce(ON_HOLD)
      .mockResolvedValueOnce({ ...ON_HOLD, status: 'IN_PROGRESS', holdReason: null, onHoldSince: null })
    addIncidentComment.mockResolvedValue({
      id: 9, type: 'COMMENT', message: 'Here it is', actorName: 'Riley Reporter',
      createdAt: '2026-01-05T12:00:00Z',
    })
    const user = userEvent.setup()
    renderAs(USERS.user)

    await user.type(await screen.findByPlaceholderText(/Add a comment/), 'Here it is')
    await user.click(screen.getByRole('button', { name: 'Comment' }))

    expect(await screen.findByText('In progress')).toBeInTheDocument()
    expect(screen.queryByText(/Waiting on reporter/)).not.toBeInTheDocument()
    expect(getIncidentById).toHaveBeenCalledTimes(2)
  })
})
