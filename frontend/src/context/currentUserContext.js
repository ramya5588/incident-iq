import { createContext, useContext } from 'react'

export const CurrentUserContext = createContext({
  users: [],
  usersError: '',
  currentUser: null,
  signOut: () => {},
  addUser: () => {},
  updateUser: () => {},
  refreshCurrentUser: () => {},
})

/**
 * The signed-in user plus the shared user list.
 * Returns { users, usersError, currentUser, signOut, addUser, updateUser,
 * refreshCurrentUser }.
 */
export function useCurrentUser() {
  return useContext(CurrentUserContext)
}
