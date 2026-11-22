/** A message to log, never the error itself, which can carry tokens. */
export const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : String(error)
