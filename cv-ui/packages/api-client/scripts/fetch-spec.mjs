// Fetches the api's openapi spec from a running api, for the typed client.
// Usage: API_SPEC_URL=http://localhost:8080/api/v3/api-docs pnpm api:spec
import { writeFileSync } from 'node:fs'

const url = process.env.API_SPEC_URL ?? 'http://localhost:8080/api/v3/api-docs'
const response = await fetch(url)
if (!response.ok) throw new Error(`Could not fetch the api spec from ${url}: ${response.status}`)

const spec = await response.json()
// The server url is wherever the spec was fetched from, so it would change on every fetch.
delete spec.servers
writeFileSync(new URL('../openapi.json', import.meta.url), `${JSON.stringify(spec, null, 2)}\n`)
console.log(`Saved the api spec from ${url}`)
