// The high and critical vulnerabilities that have no fix yet, from grype's full reports, as a table for the
// job's summary. They fail nothing, as there is nothing to apply, but they should be seen. See decisions/cve.md.
// Usage: node ci/cve/unfixed.mjs <directory of reports>
import { globSync, readFileSync } from 'node:fs'
import path from 'node:path'

const SERIOUS = new Set(['High', 'Critical'])

// One row per vulnerability, with every package and image it is in. Grype leaves the state empty, or
// says not-fixed, wont-fix or unknown, when there is nothing to upgrade to.
export const unfixed = (reports) => {
  const found = new Map()
  for (const { image, matches } of reports) {
    for (const { vulnerability, artifact } of matches) {
      if (!SERIOUS.has(vulnerability.severity) || vulnerability.fix?.state === 'fixed') continue
      const row = found.get(vulnerability.id) ?? {
        id: vulnerability.id,
        severity: vulnerability.severity,
        packages: new Set(),
        images: new Set(),
      }
      row.packages.add(`${artifact.name} ${artifact.version}`)
      row.images.add(image)
      found.set(vulnerability.id, row)
    }
  }
  return [...found.values()].sort((a, b) =>
    a.severity === b.severity ? a.id.localeCompare(b.id) : a.severity === 'Critical' ? -1 : 1,
  )
}

export const summary = (rows) =>
  rows.length === 0
    ? 'No high or critical vulnerabilities without a fix.\n'
    : [
        '| vulnerability | severity | packages | images |',
        '|---|---|---|---|',
        ...rows.map(
          ({ id, severity, packages, images }) =>
            `| ${id} | ${severity} | ${[...packages].join(', ')} | ${[...images].join(', ')} |`,
        ),
        '',
      ].join('\n')

if (import.meta.main) {
  const [reports] = process.argv.slice(2)
  if (!reports) throw new Error('Usage: node ci/cve/unfixed.mjs <directory of reports>')

  const files = globSync('**/*-all.json', { cwd: reports })
  if (files.length === 0) {
    console.log('No full reports: the scan did not finish.')
  } else {
    const rows = unfixed(
      files.map((file) => ({
        image: path.basename(file, '-all.json'),
        matches: JSON.parse(readFileSync(path.join(reports, file), 'utf8')).matches,
      })),
    )
    console.log(summary(rows))
  }
}
