import { render } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { CurrentUserContext } from '../context/currentUserContext'

export const USERS = {
  admin: { id: 'u-admin', displayName: 'Ada Admin', email: 'ada@incidentiq.test', role: 'ADMIN' },
  agent: { id: 'u-agent', displayName: 'Alex Agent', email: 'alex@incidentiq.test', role: 'AGENT' },
  user: { id: 'u-user', displayName: 'Riley Reporter', email: 'riley@incidentiq.test', role: 'USER' },
}

/** Shows the current URL so tests can check what a page wrote to it. */
function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname + location.search}</output>
}

/**
 * Renders `ui` signed in as `currentUser`, inside a router at `route`.
 * `path` is the route pattern (e.g. "/incidents/:id") when the page reads params.
 */
export function renderPage(ui, { currentUser = USERS.admin, route = '/', path = '*' } = {}) {
  const value = {
    users: Object.values(USERS),
    usersError: '',
    currentUser,
    signOut: () => {},
    addUser: () => {},
    updateUser: () => {},
    refreshCurrentUser: () => {},
  }

  return render(
    <CurrentUserContext.Provider value={value}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path={path} element={ui} />
        </Routes>
        <LocationProbe />
      </MemoryRouter>
    </CurrentUserContext.Provider>
  )
}
