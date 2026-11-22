import { render } from '@testing-library/react'
import { useErrorTitle } from './useErrorTitle'

function Page({ hasErrors }: { hasErrors: boolean }) {
  useErrorTitle(hasErrors)
  return null
}

describe('useErrorTitle', () => {
  beforeEach(() => {
    document.title = 'Your profile — Skill2Career'
  })

  it('starts the title with Error while there are errors', () => {
    render(<Page hasErrors />)

    expect(document.title).toBe('Error: Your profile — Skill2Career')
  })

  it('does not add it twice', () => {
    const { rerender } = render(<Page hasErrors />)
    rerender(<Page hasErrors />)

    expect(document.title).toBe('Error: Your profile — Skill2Career')
  })

  it('takes it off once the errors are fixed', () => {
    const { rerender } = render(<Page hasErrors />)
    rerender(<Page hasErrors={false} />)

    expect(document.title).toBe('Your profile — Skill2Career')
  })
})

describe('useErrorTitle with several forms on a page', () => {
  beforeEach(() => {
    document.title = 'Register — Skill2Career'
  })

  it('keeps Error while any form has errors, whichever renders last', () => {
    const { rerender } = render(
      <>
        <Page hasErrors />
        <Page hasErrors={false} />
      </>,
    )
    expect(document.title).toBe('Error: Register — Skill2Career')

    rerender(
      <>
        <Page hasErrors />
        <Page hasErrors={false} />
      </>,
    )
    expect(document.title).toBe('Error: Register — Skill2Career')

    rerender(
      <>
        <Page hasErrors={false} />
        <Page hasErrors={false} />
      </>,
    )
    expect(document.title).toBe('Register — Skill2Career')
  })

  it('takes it off when the form with errors goes', () => {
    const { rerender } = render(
      <>
        <Page hasErrors={false} />
        <Page hasErrors />
      </>,
    )
    rerender(<Page hasErrors={false} />)

    expect(document.title).toBe('Register — Skill2Career')
  })
})
