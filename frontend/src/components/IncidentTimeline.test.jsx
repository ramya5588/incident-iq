import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IncidentTimeline from './IncidentTimeline'
import { addIncidentComment, getIncidentEvents } from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getIncidentEvents: vi.fn(),
  addIncidentComment: vi.fn(),
}))

const REPLY = {
  id: 1,
  type: 'COMMENT',
  message: "We're looking into it",
  actorName: 'Alex Agent',
  internal: false,
  createdAt: '2026-01-05T09:00:00Z',
}
const NOTE = {
  ...REPLY,
  id: 2,
  message: 'Looks like the VPN cert expired',
  internal: true,
  createdAt: '2026-01-05T09:05:00Z',
}

function renderAs(currentUser) {
  return renderPage(<IncidentTimeline incidentId="inc-1" />, { currentUser })
}

beforeEach(() => {
  getIncidentEvents.mockReset().mockResolvedValue([NOTE, REPLY])
  addIncidentComment.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('IncidentTimeline internal notes', () => {
  it('marks internal notes so they stand apart from replies', async () => {
    renderAs(USERS.agent)

    const note = (await screen.findByText(NOTE.message)).closest('li')
    expect(within(note).getByText('Alex Agent added an internal note')).toBeInTheDocument()
    expect(within(note).getByText('Internal')).toBeInTheDocument()

    const reply = screen.getByText(REPLY.message).closest('li')
    expect(within(reply).getByText('Alex Agent commented')).toBeInTheDocument()
    expect(within(reply).queryByText('Internal')).not.toBeInTheDocument()
  })

  it('lets agents switch between a reply and an internal note', async () => {
    addIncidentComment.mockResolvedValue({ ...NOTE, id: 3, message: 'Checking with networking' })
    const user = userEvent.setup()
    renderAs(USERS.agent)
    await screen.findByText(NOTE.message)

    // A reply is the default, and says who will see it.
    expect(screen.getByRole('radio', { name: 'Reply' })).toBeChecked()
    expect(screen.getByText('The reporter will see this.')).toBeInTheDocument()

    await user.click(screen.getByRole('radio', { name: 'Internal note' }))
    expect(screen.getByText('Only agents and admins will see this.')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Internal note' }), 'Checking with networking')
    await user.click(screen.getByRole('button', { name: 'Add internal note' }))

    expect(addIncidentComment).toHaveBeenCalledWith('inc-1', 'Checking with networking', {
      internal: true,
    })
    const added = (await screen.findByText('Checking with networking')).closest('li')
    expect(within(added).getByText('Internal')).toBeInTheDocument()
  })

  it('sends replies as public comments', async () => {
    addIncidentComment.mockResolvedValue({ ...REPLY, id: 4, message: 'Try restarting' })
    const user = userEvent.setup()
    renderAs(USERS.admin)
    await screen.findByText(NOTE.message)

    await user.type(screen.getByRole('textbox', { name: 'Comment' }), 'Try restarting')
    await user.click(screen.getByRole('button', { name: 'Comment' }))

    expect(addIncidentComment).toHaveBeenCalledWith('inc-1', 'Try restarting', { internal: false })
  })

  it("doesn't offer reporters internal notes", async () => {
    getIncidentEvents.mockResolvedValue([REPLY])
    addIncidentComment.mockResolvedValue({ ...REPLY, id: 5, message: 'Thanks' })
    const user = userEvent.setup()
    renderAs(USERS.user)
    await screen.findByText(REPLY.message)

    expect(screen.queryByRole('radio', { name: 'Internal note' })).not.toBeInTheDocument()
    expect(screen.queryByText(/will see this/)).not.toBeInTheDocument()
    await user.type(screen.getByPlaceholderText('Add a comment…'), 'Thanks')
    await user.click(screen.getByRole('button', { name: 'Comment' }))

    expect(addIncidentComment).toHaveBeenCalledWith('inc-1', 'Thanks', { internal: false })
  })
})
