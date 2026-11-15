import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IncidentTimeline from './IncidentTimeline'
import { addIncidentComment, getIncidentEvents } from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'
import {
  mentionQueryAt,
  mentionSuggestions,
  splitMentions,
  withMentionMarkup,
} from '../utils/mentions'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getIncidentEvents: vi.fn(),
  addIncidentComment: vi.fn(),
}))

const ID = '11111111-2222-3333-4444-555555555555'
const OTHER_ID = '66666666-7777-8888-9999-000000000000'

function renderAs(currentUser) {
  return renderPage(<IncidentTimeline incidentId="inc-1" reporterId="u-user" />, { currentUser })
}

beforeEach(() => {
  getIncidentEvents.mockReset().mockResolvedValue([])
  addIncidentComment.mockReset().mockImplementation(async (id, body) => ({
    id: 9,
    type: 'COMMENT',
    message: body,
    actorName: 'Alex Agent',
    internal: false,
    createdAt: '2026-01-05T09:00:00Z',
  }))
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('mention helpers', () => {
  it('reads and writes mentions', () => {
    expect(splitMentions(`Hi @[Sam Patel](${ID}), thanks`)).toEqual([
      { text: 'Hi ' },
      { mention: { name: 'Sam Patel', id: ID } },
      { text: ', thanks' },
    ])
    expect(splitMentions('@[broken](not-an-id)')).toEqual([{ text: '@[broken](not-an-id)' }])

    const sam = { id: 's', displayName: 'Sam' }
    const samPatel = { id: 'sp', displayName: 'Sam Patel' }
    expect(withMentionMarkup('@Sam Patel and @Sam, not me@Sam or @Samuel', [sam, samPatel])).toBe(
      '@[Sam Patel](sp) and @[Sam](s), not me@Sam or @Samuel'
    )
    expect(withMentionMarkup('@Sam', [])).toBe('@Sam')
  })

  it('only suggests after an @ that starts a word', () => {
    expect(mentionQueryAt('Thanks @Sa', 10)).toEqual({ start: 7, query: 'Sa' })
    expect(mentionQueryAt('@', 1)).toEqual({ start: 0, query: '' })
    expect(mentionQueryAt('mail me@example', 15)).toBeNull()
    expect(mentionQueryAt('@Sam\nok', 7)).toBeNull()
  })

  it('suggests the team, and the reporter only in replies', () => {
    const users = [
      ...Object.values(USERS),
      { id: 'u-other', displayName: 'Olive Other', role: 'USER', active: true },
      { id: 'u-gone', displayName: 'Gone Agent', role: 'AGENT', active: false },
    ]
    const names = (query, options) =>
      mentionSuggestions(users, query, { reporterId: 'u-user', currentUserId: 'u-agent', ...options })
        .map((user) => user.displayName)

    expect(names('', { internal: false })).toEqual(['Ada Admin', 'Riley Reporter'])
    expect(names('', { internal: true })).toEqual(['Ada Admin'])
    expect(names('rep', { internal: false })).toEqual(['Riley Reporter'])
    expect(names('gone', { internal: false })).toEqual([])
  })
})

describe('mentioning in the comment box', () => {
  it('suggests people after @ and sends the mention', async () => {
    const user = userEvent.setup()
    renderAs(USERS.agent)
    const box = await screen.findByRole('combobox', { name: 'Comment' })

    await user.type(box, 'Thanks @')
    expect(screen.getByRole('listbox', { name: 'People to mention' })).toBeInTheDocument()
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Ada Adminadmin',
      'Riley Reporterreporter',
    ])

    await user.type(box, 'ri')
    expect(screen.getByRole('option', { name: /Riley Reporter/ })).toHaveAttribute(
      'aria-selected',
      'true'
    )
    await user.keyboard('{Enter}')
    expect(box).toHaveValue('Thanks @Riley Reporter ')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()

    await user.type(box, 'can you retry?')
    await user.click(screen.getByRole('button', { name: 'Comment' }))

    expect(addIncidentComment).toHaveBeenCalledWith(
      'inc-1',
      'Thanks @[Riley Reporter](u-user) can you retry?',
      { internal: false }
    )
    // Shown highlighted once the server returns it (with Riley's real id).
    expect(await screen.findByText('Thanks', { exact: false })).toBeInTheDocument()
  })

  it('picks with the arrow keys or the mouse, and closes on Escape', async () => {
    const user = userEvent.setup()
    renderAs(USERS.agent)
    const box = await screen.findByRole('combobox', { name: 'Comment' })

    await user.type(box, '@')
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('option', { name: /Riley/ })).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()

    await user.clear(box)
    await user.type(box, 'cc @a')
    await user.pointer({ target: screen.getByRole('option', { name: /Ada Admin/ }), keys: '[MouseLeft]' })
    expect(box).toHaveValue('cc @Ada Admin ')
  })

  it("doesn't suggest the reporter in an internal note", async () => {
    const user = userEvent.setup()
    renderAs(USERS.agent)
    await user.click(await screen.findByRole('radio', { name: 'Internal note' }))

    await user.type(screen.getByRole('combobox', { name: 'Internal note' }), '@ri')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })

  it('highlights mentions of you', async () => {
    getIncidentEvents.mockResolvedValue([
      {
        id: 1,
        type: 'COMMENT',
        message: `@[Alex Agent](${ID}) and @[Ada Admin](${OTHER_ID}), please look`,
        actorName: 'Sam',
        internal: false,
        createdAt: '2026-01-05T09:00:00Z',
      },
    ])
    renderAs({ ...USERS.agent, id: ID })

    expect(await screen.findByText('@Alex Agent')).toHaveClass('mention', 'mention-me')
    expect(screen.getByText('@Ada Admin')).not.toHaveClass('mention-me')
    expect(screen.getByText(/please look/)).toBeInTheDocument()
  })
})
