/** Shown instead of a workspace to an account without the role for it. It reveals nothing from it. */
export function AccessDenied({ workspace }: { workspace: string }) {
  return (
    <>
      <h1>You cannot access this</h1>
      <p>Your account does not have access to {workspace}.</p>
      <p>
        {/* A plain link: home is in the shell, a different app to this zone. */}
        {/* eslint-disable-next-line @next/next/no-html-link-for-pages */}
        <a href="/">Go to the home page</a>
      </p>
    </>
  )
}
