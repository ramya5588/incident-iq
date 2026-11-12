import { createContext, useContext } from 'react'

export const CreateIncidentContext = createContext({
  openCreateIncident: () => {},
})

/** Returns a function that opens the shared "Create incident" modal. */
export function useCreateIncident() {
  return useContext(CreateIncidentContext).openCreateIncident
}
