import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SatisfactionCard from './SatisfactionCard'
import { rateIncident } from '../services/incidentApi'
import { USERS } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  rateIncident: vi.fn(),
}))

const RESOLVED = {
  id: 'inc-1',
  status: 'RESOLVED',
  createdById: 'u-user',
  satisfactionScore: null,
  satisfactionComment: null,
  ratedAt: null,
}

beforeEach(() => {
  rateIncident.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('SatisfactionCard', () => {
  it('lets the reporter rate a resolved incident', async () => {
    const rated = { ...RESOLVED, satisfactionScore: 4, satisfactionComment: 'Quick fix', ratedAt: 'now' }
    rateIncident.mockResolvedValue(rated)
    const onRated = vi.fn()
    const user = userEvent.setup()
    render(<SatisfactionCard incident={RESOLVED} currentUser={USERS.user} onRated={onRated} />)

    expect(screen.getByRole('heading', { name: 'How did we do?' })).toBeInTheDocument()
    const send = screen.getByRole('button', { name: 'Send rating' })
    expect(send).toBeDisabled()

    await user.click(screen.getByRole('radio', { name: '4 out of 5: Good' }))
    expect(screen.getByText('Good')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Comment (optional)'), 'Quick fix')
    await user.click(send)

    expect(rateIncident).toHaveBeenCalledWith('inc-1', { score: 4, comment: 'Quick fix' })
    expect(onRated).toHaveBeenCalledWith(rated)
    expect(await screen.findByText('Thanks for your feedback.')).toBeInTheDocument()
  })

  it('starts from the saved rating so it can be changed', async () => {
    const incident = { ...RESOLVED, satisfactionScore: 2, satisfactionComment: 'Slow', ratedAt: 'x' }
    render(<SatisfactionCard incident={incident} currentUser={USERS.user} onRated={vi.fn()} />)

    expect(screen.getByRole('heading', { name: 'Your rating' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: '2 out of 5: Poor' })).toBeChecked()
    expect(screen.getByLabelText('Comment (optional)')).toHaveValue('Slow')
    expect(screen.getByRole('button', { name: 'Update rating' })).toBeEnabled()
  })

  it('shows the team the rating, or that there is none yet', () => {
    const { rerender } = render(
      <SatisfactionCard incident={RESOLVED} currentUser={USERS.agent} onRated={vi.fn()} />
    )
    expect(screen.getByText("The reporter hasn't rated this yet.")).toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()

    rerender(
      <SatisfactionCard
        incident={{ ...RESOLVED, satisfactionScore: 5, satisfactionComment: 'Brilliant', ratedAt: 'x' }}
        currentUser={USERS.agent}
        onRated={vi.fn()}
      />
    )
    expect(screen.getByText('5/5 · Great')).toBeInTheDocument()
    expect(screen.getByText('Brilliant')).toBeInTheDocument()
  })

  it('shows nothing while the incident is still being worked on', () => {
    const { container } = render(
      <SatisfactionCard incident={{ ...RESOLVED, status: 'IN_PROGRESS' }} currentUser={USERS.user} onRated={vi.fn()} />
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('shows why a rating could not be saved', async () => {
    rateIncident.mockRejectedValue(
      Object.assign(new Error('Request failed'), {
        response: { status: 403, data: { message: 'Only the person who reported an incident can rate it.' } },
      })
    )
    const user = userEvent.setup()
    render(<SatisfactionCard incident={RESOLVED} currentUser={USERS.user} onRated={vi.fn()} />)

    await user.click(screen.getByRole('radio', { name: '1 out of 5: Very poor' }))
    await user.click(screen.getByRole('button', { name: 'Send rating' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Only the person who reported')
  })
})
