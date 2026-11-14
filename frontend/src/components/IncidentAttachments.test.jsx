import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IncidentAttachments from './IncidentAttachments'
import CreateIncidentModal from './CreateIncidentModal'
import {
  createIncident,
  deleteAttachment,
  listAttachments,
  uploadAttachment,
} from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'
import { attachmentProblem, formatBytes } from '../utils/attachments'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  listAttachments: vi.fn(),
  uploadAttachment: vi.fn(),
  deleteAttachment: vi.fn(),
  createIncident: vi.fn(),
}))

const SHOT = {
  id: 'a-1',
  fileName: 'flicker.png',
  contentType: 'image/png',
  sizeBytes: 2048,
  image: true,
  uploadedById: 'u-user',
  uploadedByName: 'Riley Reporter',
  createdAt: '2026-01-05T09:00:00Z',
}
const LOG = {
  ...SHOT,
  id: 'a-2',
  fileName: 'app.log',
  contentType: 'text/plain;charset=UTF-8',
  image: false,
  uploadedById: 'u-agent',
  uploadedByName: 'Alex Agent',
}

function file(name, size = 10, type = 'image/png') {
  return new File([new Uint8Array(size)], name, { type })
}

beforeEach(() => {
  listAttachments.mockReset().mockResolvedValue([SHOT, LOG])
  uploadAttachment.mockReset()
  deleteAttachment.mockReset()
  createIncident.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('attachment helpers', () => {
  it('sizes and checks files', () => {
    expect(formatBytes(12)).toBe('12 bytes')
    expect(formatBytes(2048)).toBe('2 KB')
    expect(formatBytes(2.5 * 1024 * 1024)).toBe('2.5 MB')
    expect(attachmentProblem(file('shot.PNG'))).toBe('')
    expect(attachmentProblem(file('tool.exe'))).toMatch(/only images/)
    expect(attachmentProblem(file('big.png', 5 * 1024 * 1024 + 1))).toMatch(/at most 5 MB/)
    expect(attachmentProblem(file('empty.txt', 0))).toBe('empty.txt is empty.')
  })
})

describe('IncidentAttachments', () => {
  it('shows images as thumbnails and other files as downloads', async () => {
    render(<IncidentAttachments incidentId="inc-1" canAttach currentUser={USERS.user} />)

    const thumb = await screen.findByRole('img', { name: 'flicker.png' })
    expect(thumb).toHaveAttribute('src', '/api/incidents/inc-1/attachments/a-1')
    const log = screen.getByRole('link', { name: 'app.log' })
    expect(log).toHaveAttribute('download', 'app.log')
    expect(screen.getByText(/2 KB · Alex Agent/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Attachments (2)' })).toBeInTheDocument()

    // Riley can remove their own file, not Alex's.
    expect(screen.getByRole('button', { name: 'Remove flicker.png' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove app.log' })).not.toBeInTheDocument()
  })

  it('uploads chosen files one by one', async () => {
    uploadAttachment.mockImplementation(async (incidentId, chosen) => ({
      ...SHOT,
      id: `new-${chosen.name}`,
      fileName: chosen.name,
    }))
    const onChanged = vi.fn()
    const user = userEvent.setup()
    render(
      <IncidentAttachments incidentId="inc-1" canAttach currentUser={USERS.user} onChanged={onChanged} />
    )
    await screen.findByRole('img', { name: 'flicker.png' })

    await user.upload(screen.getByLabelText(/choose files to attach/), [file('one.png'), file('two.png')])

    expect(uploadAttachment).toHaveBeenCalledTimes(2)
    expect(await screen.findByRole('img', { name: 'two.png' })).toBeInTheDocument()
    expect(onChanged).toHaveBeenCalledTimes(2)
  })

  it('stops files that would be refused before sending them', async () => {
    uploadAttachment.mockRejectedValue(
      Object.assign(new Error('Request failed'), {
        response: { status: 400, data: { message: "That kind of file can't be attached." } },
      })
    )
    const user = userEvent.setup({ applyAccept: false })
    render(<IncidentAttachments incidentId="inc-1" canAttach currentUser={USERS.user} />)
    await screen.findByRole('img', { name: 'flicker.png' })

    await user.upload(screen.getByLabelText(/choose files to attach/), [
      file('tool.exe'),
      file('big.png', 6 * 1024 * 1024),
      file('fake.png'),
    ])

    // Only the one that looked fine went to the server, and its refusal is shown.
    expect(uploadAttachment).toHaveBeenCalledTimes(1)
    const alert = screen.getByRole('alert')
    expect(within(alert).getByText(/tool.exe: only images/)).toBeInTheDocument()
    expect(within(alert).getByText(/big.png is 6.0 MB/)).toBeInTheDocument()
    expect(within(alert).getByText("fake.png: That kind of file can't be attached.")).toBeInTheDocument()
  })

  it('asks before removing a file', async () => {
    deleteAttachment.mockResolvedValue()
    const onChanged = vi.fn()
    const user = userEvent.setup()
    render(
      <IncidentAttachments incidentId="inc-1" canAttach currentUser={USERS.admin} onChanged={onChanged} />
    )

    await user.click(await screen.findByRole('button', { name: 'Remove app.log' }))
    expect(deleteAttachment).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Remove' }))

    expect(deleteAttachment).toHaveBeenCalledWith('inc-1', 'a-2')
    expect(screen.queryByRole('link', { name: 'app.log' })).not.toBeInTheDocument()
    expect(onChanged).toHaveBeenCalled()
  })

  it('has no upload area for people who can’t attach', async () => {
    listAttachments.mockResolvedValue([])
    render(<IncidentAttachments incidentId="inc-1" canAttach={false} currentUser={USERS.user} />)

    expect(await screen.findByText('No attachments.')).toBeInTheDocument()
    expect(screen.queryByLabelText(/choose files to attach/)).not.toBeInTheDocument()
  })
})

describe('CreateIncidentModal attachments', () => {
  it('attaches chosen files after creating the incident and reports any that failed', async () => {
    createIncident.mockResolvedValue({ id: 'inc-9' })
    uploadAttachment
      .mockResolvedValueOnce(SHOT)
      .mockRejectedValueOnce(
        Object.assign(new Error('Request failed'), {
          response: { status: 400, data: { message: 'Files can be at most 5 MB.' } },
        })
      )
    const onCreated = vi.fn()
    const user = userEvent.setup()
    renderPage(<CreateIncidentModal onClose={vi.fn()} onCreated={onCreated} />, {
      currentUser: USERS.user,
    })

    await user.type(screen.getByLabelText('Title'), 'Screen flickers')
    await user.type(screen.getByLabelText('Description'), 'See attached')
    await user.type(screen.getByLabelText('Category'), 'Hardware')
    await user.upload(screen.getByLabelText('Attachments (optional)'), [file('a.png'), file('b.png')])
    expect(screen.getByText('a.png')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create incident' }))

    expect(uploadAttachment).toHaveBeenNthCalledWith(1, 'inc-9', expect.any(File))
    expect(onCreated).toHaveBeenCalledWith({ id: 'inc-9' }, ['b.png: Files can be at most 5 MB.'])
  })

  it("won't submit files it already knows will be refused", async () => {
    const user = userEvent.setup({ applyAccept: false })
    renderPage(<CreateIncidentModal onClose={vi.fn()} onCreated={vi.fn()} />, {
      currentUser: USERS.user,
    })

    await user.upload(screen.getByLabelText('Attachments (optional)'), file('tool.exe'))

    expect(screen.getByText(/tool.exe: only images/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create incident' })).toBeDisabled()
  })
})
