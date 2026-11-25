// A mutation test's score, and the mutants that survived, for the job's summary, from stryker's json
// report or pitest's xml one. Informational: it fails nothing. See decisions/mutation.md.
// Usage: node ci/mutation/summary.mjs <stryker|pitest> <name> <report>...
import { readFileSync } from 'node:fs'

// Most survivors are worth a look, but a table of hundreds is not read. The rest are in the report.
const SHOWN = 50

// Stryker's statuses. Ignored and broken mutants count for neither side, as stryker scores them.
const STRYKER_DETECTED = new Set(['Killed', 'Timeout'])
const STRYKER_MISSED = new Set(['Survived', 'NoCoverage'])

export const fromStryker = (report) =>
  Object.entries(report.files).flatMap(([file, { mutants }]) =>
    mutants
      .filter(({ status }) => STRYKER_DETECTED.has(status) || STRYKER_MISSED.has(status))
      .map(({ status, mutatorName, location, replacement }) => ({
        detected: STRYKER_DETECTED.has(status),
        file,
        line: location.start.line,
        mutator: mutatorName,
        change: replacement ?? '',
      })),
  )

// Pitest's statuses. A mutant that could not be loaded tells nothing about the tests.
const PITEST_DETECTED = new Set(['KILLED', 'TIMED_OUT', 'MEMORY_ERROR', 'RUN_ERROR'])
const PITEST_MISSED = new Set(['SURVIVED', 'NO_COVERAGE'])

// The xml's escapes, with `&amp;` last, so an escaped escape such as `&amp;quot;` stays `&quot;`.
const unescape = (value) =>
  value
    .replaceAll('&quot;', '"')
    .replaceAll('&apos;', "'")
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&amp;', '&')

const text = (block, tag) =>
  unescape(block.match(new RegExp(`<${tag}>([^<]*)</${tag}>`))?.[1] ?? '')

export const fromPitest = (xml) =>
  [...xml.matchAll(/<mutation\b[^>]*status='([A-Z_]+)'[^>]*>([\s\S]*?)<\/mutation>/g)]
    .filter(([, status]) => PITEST_DETECTED.has(status) || PITEST_MISSED.has(status))
    .map(([, status, block]) => ({
      detected: PITEST_DETECTED.has(status),
      file: `${text(block, 'mutatedClass').split('$')[0]}.${text(block, 'mutatedMethod')}`,
      line: Number(text(block, 'lineNumber')),
      mutator: text(block, 'mutator').split('.').pop(),
      change: text(block, 'description'),
    }))

const cell = (value) => String(value).replaceAll('|', '\\|').replaceAll('\n', ' ')

export const summary = (name, mutants) => {
  const missed = mutants.filter(({ detected }) => !detected)
  const score =
    mutants.length === 0
      ? '-'
      : `${((100 * (mutants.length - missed.length)) / mutants.length).toFixed(1)}%`
  const lines = [
    `### Mutation testing: ${name}`,
    '',
    `Score ${score}: ${mutants.length - missed.length} of ${mutants.length} mutants caught by a test. Only the junit or jest tests are run against mutants, not the smoke, playwright or attack tests, so behaviour only they check shows here as survived.`,
    '',
  ]
  if (missed.length > 0) {
    lines.push(
      '| where | line | mutation | change |',
      '|---|---|---|---|',
      ...missed
        .slice(0, SHOWN)
        .map(
          ({ file, line, mutator, change }) =>
            `| ${cell(file)} | ${line} | ${mutator} | ${cell(change)} |`,
        ),
      '',
    )
    if (missed.length > SHOWN) lines.push(`And ${missed.length - SHOWN} more, in the report.`, '')
  }
  return lines.join('\n')
}

// Several reports, scored as one: the api's modules each write their own, as pitest's aggregator merges
// only the html.
export const fromReports = (tool, contents) =>
  contents.flatMap((content) =>
    tool === 'stryker' ? fromStryker(JSON.parse(content)) : fromPitest(content),
  )

if (import.meta.main) {
  const [tool, name, ...reports] = process.argv.slice(2)
  if (!['stryker', 'pitest'].includes(tool) || !name || reports.length === 0)
    throw new Error('Usage: node ci/mutation/summary.mjs <stryker|pitest> <name> <report>...')

  console.log(
    summary(
      name,
      fromReports(
        tool,
        reports.map((report) => readFileSync(report, 'utf8')),
      ),
    ),
  )
}
