import { getSession } from '@cv/auth/server'
import { areasFor } from './areas'

export default async function Home() {
  const user = (await getSession())?.user
  if (!user) return <h1>Skill2Career</h1>

  const areas = areasFor(user)
  return (
    <>
      <h1>Welcome, {user.name}</h1>
      {areas.length === 0 ? (
        <p>Your account does not have access to anything yet.</p>
      ) : (
        <nav aria-label="Your areas">
          <ul>
            {areas.map(({ name, href }) => (
              <li key={href}>
                {/* Each area is a separate zone, so this must be a full page load, not a client side Link. */}
                <a href={href}>{name}</a>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </>
  )
}
