import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DuplicatePanel from './DuplicatePanel'
import IncidentsList from '../pages/IncidentsList'
import { getDuplicates, getIncidentPage, markDuplicate, undoDuplicate } from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'
import { incidentLabel, parseIncidentNumber } from '../utils/incidentNumbers'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getDuplicates: vi.fn(),
  getIncidentPage: vi.fn(),
  markDuplicate: vi.fn(),
  undoDuplicate: vi.fn(),
  getUnreadNotificationCount: vi.fn().mockResolvedValue(0),
}))

const INCIDENT = {
  id: 'inc-12',
  incidentNumber: 12,
  title: 'Printer not working',
  status: 'OPEN',
  duplicateOfId: null,
  updatedAt: '2026-01-05T09:00:00Z',
}
const MAIN = { id: 'inc-9', incidentNumber: 9, title: 'Printer offline' }

function renderPanel(incident = INCIDENT, { canWork = true, onChanged = vi.fn() } = {}) {
  renderPage(<DuplicatePanel incident={incident} canWork={canWork} onChanged={onChanged} />)
  return onChanged
}

beforeEach(() => {
  getDuplicates.mockReset().mockResolvedValue([])
  getIncidentPage.mockReset().mockResolvedValue({ content: [MAIN], totalElements: 1, totalPages: 1 })
  markDuplicate.mockReset()
  undoDuplicate.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('incident numbers', () => {
  it('reads the ways people write them', () => {
    expect(parseIncidentNumber('INC-009')).toBe(9)
    expect(parseIncidentNumber(' inc 9 ')).toBe(9)
    expect(parseIncidentNumber('#12')).toBe(12)
    expect(parseIncidentNumber('42')).toBe(42)
    expect(parseIncidentNumber('0')).toBeNull()
    expect(parseIncidentNumber('printer')).toBeNull()
    expect(incidentLabel(7)).toBe('INC-007')
  })
})

describe('DuplicatePanel', () => {
  it('finds the main incident and asks before merging', async () => {
    const closed = { ...INCIDENT, status: 'CLOSED', duplicateOfId: 'inc-9', duplicateOfNumber: 9, duplicateOfTitle: 'Printer offline' }
    markDuplicate.mockResolvedValue(closed)
    const user = userEvent.setup()
    const onChanged = renderPanel()

    await user.click(screen.getByRole('button', { name: 'Mark as duplicate…' }))
    await user.type(screen.getByLabelText('Duplicate of which incident?'), 'INC-009')
    await user.click(screen.getByRole('button', { name: 'Find' }))

    expect(getIncidentPage).toHaveBeenCalledWith({ q: 'INC-9', size: 5 })
    expect(await screen.findByRole('alertdialog', { name: 'Confirm duplicate' })).toHaveTextContent(
      'Close INC-012 as a duplicate of INC-009 “Printer offline”?'
    )
    expect(markDuplicate).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Mark as duplicate' }))

    expect(markDuplicate).toHaveBeenCalledWith('inc-12', 'inc-9')
    expect(onChanged).toHaveBeenCalledWith(closed)
  })

  it('explains numbers it cannot use', async () => {
    getIncidentPage.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 })
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByRole('button', { name: 'Mark as duplicate…' }))
    const box = screen.getByLabelText('Duplicate of which incident?')
    await user.type(box, 'printer')
    await user.click(screen.getByRole('button', { name: 'Find' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter an incident number')

    await user.clear(box)
    await user.type(box, '12')
    await user.click(screen.getByRole('button', { name: 'Find' }))
    expect(screen.getByRole('alert')).toHaveTextContent("can't be a duplicate of itself")

    await user.clear(box)
    await user.type(box, '77')
    await user.click(screen.getByRole('button', { name: 'Find' }))
    expect(await screen.findByRole('alert')).toHaveTextContent("There's no INC-077.")
  })

  it('shows the server’s reason when a merge is refused', async () => {
    markDuplicate.mockRejectedValue(
      Object.assign(new Error('Request failed'), {
        response: { status: 400, data: { message: 'INC-009 is itself a duplicate of INC-003. Choose that one instead.' } },
      })
    )
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByRole('button', { name: 'Mark as duplicate…' }))
    await user.type(screen.getByLabelText('Duplicate of which incident?'), '9')
    await user.click(screen.getByRole('button', { name: 'Find' }))
    await user.click(await screen.findByRole('button', { name: 'Mark as duplicate' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Choose that one instead.')
  })

  it('links a duplicate to its main incident and can undo', async () => {
    const reopened = { ...INCIDENT }
    undoDuplicate.mockResolvedValue(reopened)
    const user = userEvent.setup()
    const onChanged = renderPanel({
      ...INCIDENT,
      status: 'CLOSED',
      duplicateOfId: 'inc-9',
      duplicateOfNumber: 9,
      duplicateOfTitle: 'Printer offline',
    })

    expect(screen.getByRole('link', { name: 'INC-009 “Printer offline”' })).toHaveAttribute('href', '/incidents/inc-9')
    expect(screen.queryByRole('button', { name: 'Mark as duplicate…' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Undo' }))

    expect(undoDuplicate).toHaveBeenCalledWith('inc-12')
    expect(onChanged).toHaveBeenCalledWith(reopened)
  })

  it('lists the duplicates merged into the main incident', async () => {
    getDuplicates.mockResolvedValue([{ id: 'inc-12', incidentNumber: 12, title: 'Printer not working' }])
    renderPanel({ ...INCIDENT, id: 'inc-9', incidentNumber: 9 })

    expect(await screen.findByRole('link', { name: 'INC-012 “Printer not working”' })).toBeInTheDocument()
    // A main incident with duplicates can't become a duplicate itself.
    expect(screen.queryByRole('button', { name: 'Mark as duplicate…' })).not.toBeInTheDocument()
  })

  it('offers reporters no merging', async () => {
    renderPanel({ ...INCIDENT, status: 'CLOSED', duplicateOfId: 'inc-9', duplicateOfNumber: 9, duplicateOfTitle: 'Printer offline' }, { canWork: false })

    expect(screen.getByText(/Closed as a duplicate of/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Undo' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Mark as duplicate…' })).not.toBeInTheDocument()
  })
})

describe('duplicates on the incident list', () => {
  it('marks them with a badge', async () => {
    getIncidentPage.mockResolvedValue({
      content: [
        { ...INCIDENT, status: 'CLOSED', duplicateOfId: 'inc-9', duplicateOfNumber: 9, priority: 'LOW', category: 'Hardware', createdAt: '2026-01-05T09:00:00Z' },
        { ...MAIN, status: 'OPEN', priority: 'LOW', category: 'Hardware', createdAt: '2026-01-05T09:00:00Z' },
      ],
      totalElements: 2,
      totalPages: 1,
    })
    renderPage(<IncidentsList />, { currentUser: USERS.agent, route: '/incidents' })

    const badge = await screen.findByText('Duplicate')
    expect(badge).toHaveAttribute('title', 'Duplicate of INC-009')
    expect(screen.getAllByText('Duplicate')).toHaveLength(1)
  })
})
