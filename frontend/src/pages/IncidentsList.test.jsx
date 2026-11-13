import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IncidentsList from './IncidentsList'
import { getIncidentPage } from '../services/incidentApi'
import { renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getIncidentPage: vi.fn(),
}))

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0 }

const location = () => screen.getByTestId('location').textContent

describe('IncidentsList search', () => {
  beforeEach(() => {
    getIncidentPage.mockReset()
    getIncidentPage.mockResolvedValue(EMPTY_PAGE)
  })

  it('searches after a pause and keeps the search in the URL', async () => {
    const user = userEvent.setup()
    renderPage(<IncidentsList />, { route: '/incidents' })
    await waitFor(() => expect(getIncidentPage).toHaveBeenCalledTimes(1))

    await user.type(screen.getByRole('searchbox', { name: 'Search incidents' }), 'vpn')

    await waitFor(() => expect(location()).toBe('/incidents?q=vpn'))
    await waitFor(() =>
      expect(getIncidentPage).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'vpn', page: 0 }))
    )
    // One request for the initial load and one for the search, not one per key.
    expect(getIncidentPage).toHaveBeenCalledTimes(2)
    expect(await screen.findByText('No incidents match “vpn”.')).toBeInTheDocument()
  })

  it('fills the box from a shared link and Escape clears it', async () => {
    const user = userEvent.setup()
    renderPage(<IncidentsList />, { route: '/incidents?q=printer&status=OPEN' })

    const box = screen.getByRole('searchbox', { name: 'Search incidents' })
    expect(box).toHaveValue('printer')
    await waitFor(() =>
      expect(getIncidentPage).toHaveBeenCalledWith(
        expect.objectContaining({ q: 'printer', status: 'OPEN' })
      )
    )

    await user.click(box)
    await user.keyboard('{Escape}')

    expect(box).toHaveValue('')
    await waitFor(() => expect(location()).toBe('/incidents?status=OPEN'))
  })

  it('turns the overdue filter on and off through the URL', async () => {
    const user = userEvent.setup()
    renderPage(<IncidentsList />, { route: '/incidents' })

    await user.click(screen.getByRole('checkbox', { name: 'Overdue only' }))

    await waitFor(() => expect(location()).toBe('/incidents?overdue=true'))
    await waitFor(() =>
      expect(getIncidentPage).toHaveBeenLastCalledWith(expect.objectContaining({ overdue: true }))
    )
    expect(await screen.findByText('Nothing is overdue. Nice work.')).toBeInTheDocument()
  })
})
