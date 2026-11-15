import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IncidentsList from './IncidentsList'
import { bulkUpdateIncidents, getIncidentPage } from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getIncidentPage: vi.fn(),
  bulkUpdateIncidents: vi.fn(),
  getUnreadNotificationCount: vi.fn().mockResolvedValue(0),
}))

function incident(number, title) {
  return {
    id: `inc-${number}`,
    incidentNumber: number,
    title,
    category: 'Network',
    priority: 'LOW',
    status: 'OPEN',
    assignedToName: null,
    createdAt: '2026-01-05T09:00:00Z',
  }
}

const PAGE = {
  content: [incident(1, 'VPN drops'), incident(2, 'Printer jams'), incident(3, 'Wi-Fi slow')],
  totalElements: 3,
  totalPages: 1,
}

function renderAs(currentUser, route = '/incidents') {
  return renderPage(<IncidentsList />, { currentUser, route })
}

beforeEach(() => {
  getIncidentPage.mockReset().mockResolvedValue(PAGE)
  bulkUpdateIncidents.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('IncidentsList bulk changes', () => {
  it('changes the selected incidents and reports any that failed', async () => {
    bulkUpdateIncidents.mockResolvedValue({
      updated: 1,
      failed: [{ incidentId: 'inc-3', message: 'Incident not found' }],
    })
    const user = userEvent.setup()
    renderAs(USERS.agent)

    await user.click(await screen.findByRole('checkbox', { name: 'Select INC-001' }))
    await user.click(screen.getByRole('checkbox', { name: 'Select INC-003' }))
    const bar = screen.getByRole('form', { name: 'Change selected incidents' })
    expect(within(bar).getByText('2 selected')).toBeInTheDocument()

    const apply = within(bar).getByRole('button', { name: 'Apply to 2 incidents' })
    expect(apply).toBeDisabled()
    await user.selectOptions(within(bar).getByLabelText('Priority'), 'HIGH')
    await user.selectOptions(within(bar).getByLabelText('Assignee'), 'u-admin')
    await user.click(apply)

    expect(bulkUpdateIncidents).toHaveBeenCalledWith({
      incidentIds: ['inc-1', 'inc-3'],
      priority: 'HIGH',
      assigneeId: 'u-admin',
    })
    const result = (await screen.findByText('Updated 1 incident.')).closest('.bulk-result')
    expect(result).toHaveAttribute('role', 'status')
    expect(within(result).getByText('INC-003: Incident not found')).toBeInTheDocument()

    // The list reloads and the selection is cleared.
    await waitFor(() => expect(getIncidentPage).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('form', { name: 'Change selected incidents' })).not.toBeInTheDocument()
  })

  it('asks why before putting incidents on hold, and can unassign', async () => {
    bulkUpdateIncidents.mockResolvedValue({ updated: 3, failed: [] })
    const user = userEvent.setup()
    renderAs(USERS.admin)

    await user.click(await screen.findByRole('checkbox', { name: 'Select all incidents on this page' }))
    const bar = screen.getByRole('form', { name: 'Change selected incidents' })
    await user.selectOptions(within(bar).getByLabelText('Status'), 'ON_HOLD')
    const apply = within(bar).getByRole('button', { name: 'Apply to 3 incidents' })
    expect(apply).toBeDisabled()

    await user.selectOptions(within(bar).getByLabelText('Waiting for'), 'WAITING_ON_THIRD_PARTY')
    await user.type(within(bar).getByLabelText('Note (optional)'), 'Vendor ticket 42')
    await user.selectOptions(within(bar).getByLabelText('Assignee'), 'unassign')
    await user.click(apply)

    expect(bulkUpdateIncidents).toHaveBeenCalledWith({
      incidentIds: ['inc-1', 'inc-2', 'inc-3'],
      status: 'ON_HOLD',
      holdReason: 'WAITING_ON_THIRD_PARTY',
      holdNote: 'Vendor ticket 42',
      unassign: true,
    })
    expect(await screen.findByText('Updated 3 incidents.')).toBeInTheDocument()
  })

  it('selects all on the page and clears the selection when the page changes', async () => {
    const user = userEvent.setup()
    renderAs(USERS.agent)

    const all = await screen.findByRole('checkbox', { name: 'Select all incidents on this page' })
    await user.click(screen.getByRole('checkbox', { name: 'Select INC-002' }))
    expect(all).not.toBeChecked()
    expect(all.indeterminate).toBe(true)

    await user.click(all)
    expect(screen.getAllByRole('checkbox', { name: /^Select INC-/ }).every((box) => box.checked)).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Clear selection' }))
    expect(screen.queryByText(/selected$/)).not.toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: 'Select INC-001' }))
    // The list's own Status filter (the first one; the bar has another).
    await user.selectOptions(screen.getAllByRole('combobox', { name: 'Status' })[0], 'RESOLVED')
    // A new filter: the old selection no longer applies.
    expect(screen.queryByText('1 selected')).not.toBeInTheDocument()
  })

  it('shows the error when nothing could be changed', async () => {
    bulkUpdateIncidents.mockRejectedValue(
      Object.assign(new Error('Request failed'), {
        response: { status: 403, data: { message: 'Forbidden' } },
      })
    )
    const user = userEvent.setup()
    renderAs(USERS.agent)

    await user.click(await screen.findByRole('checkbox', { name: 'Select INC-001' }))
    const bar = screen.getByRole('form', { name: 'Change selected incidents' })
    await user.selectOptions(within(bar).getByLabelText('Status'), 'CLOSED')
    await user.click(within(bar).getByRole('button', { name: 'Apply to 1 incident' }))

    expect(await within(bar).findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Select INC-001' })).toBeChecked()
  })

  it('has no selection for reporters', async () => {
    renderAs(USERS.user)

    expect(await screen.findByText('VPN drops')).toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: /Select/ })).not.toBeInTheDocument()
  })
})
