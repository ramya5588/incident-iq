import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IncidentDetails from './IncidentDetails'
import { getIncidentById, getIncidentEvents } from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getIncidentById: vi.fn(),
  getIncidentEvents: vi.fn(),
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
