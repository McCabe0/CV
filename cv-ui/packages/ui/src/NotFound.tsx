/** Shown, with a 404, for a page that does not exist or that the api does not let the user see. */
export function NotFound() {
  return (
    <>
      <h1>Page not found</h1>
      <p>If you typed the web address, check it is correct.</p>
      <p>If you pasted the web address, check you copied the entire address.</p>
      <p>
        {/* A plain link: home is in the shell, a different app to a zone. */}
        {/* eslint-disable-next-line @next/next/no-html-link-for-pages */}
        <a href="/">Go to the home page</a>
      </p>
    </>
  )
}
