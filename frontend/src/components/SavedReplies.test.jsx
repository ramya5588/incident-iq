import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IncidentTimeline from './IncidentTimeline'
import SavedRepliesManager from './SavedRepliesManager'
import {
  addIncidentComment,
  createSavedReply,
  deleteSavedReply,
  getIncidentEvents,
  listSavedReplies,
  updateSavedReply,
} from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'
import { fillPlaceholders, matchReplies } from '../utils/savedReplies'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getIncidentEvents: vi.fn(),
  addIncidentComment: vi.fn(),
  listSavedReplies: vi.fn(),
  createSavedReply: vi.fn(),
  updateSavedReply: vi.fn(),
  deleteSavedReply: vi.fn(),
}))

const RESTART = {
  id: 'r-1',
  title: 'Restart',
  body: 'Hi {reporter}, please restart and tell us if {incident} is fixed. {agent}',
  shared: true,
  canEdit: false,
}
const SCREENSHOT = {
  id: 'r-2',
  title: 'Screenshot',
  body: 'Can you send a screenshot?',
  shared: false,
  canEdit: true,
}

beforeEach(() => {
  getIncidentEvents.mockReset().mockResolvedValue([])
  addIncidentComment.mockReset()
  listSavedReplies.mockReset().mockResolvedValue([RESTART, SCREENSHOT])
  createSavedReply.mockReset()
  updateSavedReply.mockReset()
  deleteSavedReply.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('saved reply helpers', () => {
  it('fills in placeholders and searches', () => {
    expect(fillPlaceholders('Hi {reporter} about {incident}, {other}', { reporter: 'Riley', incident: 'INC-007' }))
      .toBe('Hi Riley about INC-007, {other}')
    expect(fillPlaceholders('Hi {agent}', {})).toBe('Hi {agent}')
    expect(matchReplies([RESTART, SCREENSHOT], 'SCREEN').map((r) => r.id)).toEqual(['r-2'])
    expect(matchReplies([RESTART, SCREENSHOT], 'please restart').map((r) => r.id)).toEqual(['r-1'])
    expect(matchReplies([RESTART, SCREENSHOT], ' ')).toHaveLength(2)
  })
})

describe('inserting a saved reply', () => {
  function renderTimeline(currentUser) {
    return renderPage(
      <IncidentTimeline incidentId="inc-1" incidentNumber={7} reporterId="u-user" />,
      { currentUser }
    )
  }

  it('fills in the placeholders and puts it at the cursor', async () => {
    const user = userEvent.setup()
    renderTimeline(USERS.agent)
    const box = await screen.findByRole('combobox', { name: 'Comment' })
    await user.type(box, 'Thanks!')
    box.setSelectionRange(0, 0)

    await user.click(screen.getByRole('button', { name: 'Saved replies' }))
    const dialog = await screen.findByRole('dialog', { name: 'Saved replies' })
    expect(within(dialog).getByText('Personal')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: /Restart/ }))

    expect(box).toHaveValue(
      'Hi Riley Reporter, please restart and tell us if INC-007 is fixed. Alex AgentThanks!'
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    // The list is loaded once.
    await user.click(screen.getByRole('button', { name: 'Saved replies' }))
    expect(listSavedReplies).toHaveBeenCalledTimes(1)
  })

  it('searches the list and closes on Escape', async () => {
    const user = userEvent.setup()
    renderTimeline(USERS.admin)

    await user.click(await screen.findByRole('button', { name: 'Saved replies' }))
    await user.type(await screen.findByRole('searchbox', { name: 'Search saved replies' }), 'shot')
    const dialog = screen.getByRole('dialog', { name: 'Saved replies' })
    expect(within(dialog).queryByText('Restart')).not.toBeInTheDocument()
    expect(within(dialog).getByText('Screenshot')).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('says where to add replies when there are none', async () => {
    listSavedReplies.mockResolvedValue([])
    const user = userEvent.setup()
    renderTimeline(USERS.agent)

    await user.click(await screen.findByRole('button', { name: 'Saved replies' }))
    expect(await screen.findByText(/Add them on your Account page/)).toBeInTheDocument()
  })

  it("isn't offered to reporters", async () => {
    renderTimeline(USERS.user)

    expect(await screen.findByRole('combobox', { name: 'Comment' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Saved replies' })).not.toBeInTheDocument()
  })
})

describe('SavedRepliesManager', () => {
  it('lets agents add personal replies and edit only their own', async () => {
    createSavedReply.mockResolvedValue({})
    const user = userEvent.setup()
    render(<SavedRepliesManager admin={false} />)

    expect(await screen.findByText('Restart')).toBeInTheDocument()
    // The shared one can't be changed by an agent.
    expect(screen.queryByRole('button', { name: 'Edit Restart' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Screenshot' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Add a reply' }))
    expect(screen.queryByLabelText('Share with the team')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Title'), 'Escalated')
    await user.type(screen.getByLabelText('Text'), 'Passed to networking.')
    await user.click(screen.getByRole('button', { name: 'Add reply' }))

    expect(createSavedReply).toHaveBeenCalledWith({
      title: 'Escalated',
      body: 'Passed to networking.',
      personal: true,
    })
    expect(listSavedReplies).toHaveBeenCalledTimes(2)
  })

  it('lets admins share replies with the team', async () => {
    createSavedReply.mockResolvedValue({})
    const user = userEvent.setup()
    render(<SavedRepliesManager admin />)

    await user.click(await screen.findByRole('button', { name: 'Add a reply' }))
    expect(screen.getByLabelText('Share with the team')).toBeChecked()
    await user.type(screen.getByLabelText('Title'), 'Hello')
    await user.type(screen.getByLabelText('Text'), 'Hi {{reporter}')
    await user.click(screen.getByRole('button', { name: 'Add reply' }))

    expect(createSavedReply).toHaveBeenCalledWith({ title: 'Hello', body: 'Hi {reporter}', personal: false })
  })

  it('edits and deletes after confirming', async () => {
    updateSavedReply.mockResolvedValue({})
    deleteSavedReply.mockResolvedValue()
    const user = userEvent.setup()
    render(<SavedRepliesManager admin={false} />)

    await user.click(await screen.findByRole('button', { name: 'Edit Screenshot' }))
    const title = screen.getByLabelText('Title')
    await user.clear(title)
    await user.type(title, 'Screenshot please')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(updateSavedReply).toHaveBeenCalledWith('r-2', {
      title: 'Screenshot please',
      body: 'Can you send a screenshot?',
    })

    await user.click(await screen.findByRole('button', { name: 'Delete Screenshot' }))
    expect(deleteSavedReply).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(deleteSavedReply).toHaveBeenCalledWith('r-2')
  })

  it('shows why a reply could not be saved', async () => {
    createSavedReply.mockRejectedValue(
      Object.assign(new Error('Request failed'), {
        response: { status: 403, data: { message: 'You can keep at most 50 personal replies.' } },
      })
    )
    const user = userEvent.setup()
    render(<SavedRepliesManager admin={false} />)

    await user.click(await screen.findByRole('button', { name: 'Add a reply' }))
    await user.type(screen.getByLabelText('Title'), 'One more')
    await user.type(screen.getByLabelText('Text'), 'Text')
    await user.click(screen.getByRole('button', { name: 'Add reply' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('at most 50 personal replies')
  })
})
