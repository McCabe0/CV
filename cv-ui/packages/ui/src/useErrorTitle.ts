'use client'

import { useEffect, useId } from 'react'

const PREFIX = 'Error: '

// The forms on the page that have errors. A page can have several, such as the register's.
const withErrors = new Set<string>()

const setTitle = () => {
  const title = document.title.startsWith(PREFIX)
    ? document.title.slice(PREFIX.length)
    : document.title
  document.title = withErrors.size > 0 ? `${PREFIX}${title}` : title
}

/**
 * gov.uk: the page title starts with Error while a form has errors, so screen reader users hear it first. With
 * several forms on a page, it stays while any of them has errors.
 */
export function useErrorTitle(hasErrors: boolean) {
  const id = useId()

  useEffect(() => {
    if (hasErrors) withErrors.add(id)
    else withErrors.delete(id)
    setTitle()
    return () => {
      withErrors.delete(id)
      setTitle()
    }
  })
}
