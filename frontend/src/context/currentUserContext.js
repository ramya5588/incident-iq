import { createContext, useContext } from 'react'

export const CurrentUserContext = createContext({
  users: [],
  usersError: '',
  currentUser: null,
  setCurrentUserId: () => {},
  addUser: () => {},
})

/**
 * The acting user (stand-in for real sign-in) plus the shared user list.
 * Returns { users, usersError, currentUser, setCurrentUserId, addUser }.
 */
export function useCurrentUser() {
  return useContext(CurrentUserContext)
}
