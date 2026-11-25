// node --test ci/mutation
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { fromPitest, fromReports, fromStryker, summary } from './summary.mjs'

const strykerMutant = (status, line = 1) => ({
  status,
  mutatorName: 'EqualityOperator',
  location: { start: { line } },
  replacement: 'a <= b',
})

const pitestMutant = (status, line = 10) =>
  `<mutation detected='${status === 'KILLED'}' status='${status}' numberOfTestsRun='1'><sourceFile>Profile.kt</sourceFile><mutatedClass>com.skill2career.domain.Profile$Companion</mutatedClass><mutatedMethod>amend</mutatedMethod><lineNumber>${line}</lineNumber><mutator>org.pitest.mutationtest.engine.gregor.mutators.VoidMethodCallMutator</mutator><description>removed call to setHeadline</description></mutation>`

describe('fromStryker', () => {
  it('counts killed and timed out mutants as caught, and survived and uncovered ones as not', () => {
    const mutants = fromStryker({
      files: {
        'src/session.ts': {
          mutants: ['Killed', 'Timeout', 'Survived', 'NoCoverage'].map((status) =>
            strykerMutant(status),
          ),
        },
      },
    })

    assert.deepEqual(
      mutants.map(({ detected }) => detected),
      [true, true, false, false],
    )
    assert.deepEqual(mutants[2], {
      detected: false,
      file: 'src/session.ts',
      line: 1,
      mutator: 'EqualityOperator',
      change: 'a <= b',
    })
  })

  it('leaves out ignored and broken mutants, which say nothing about the tests', () => {
    const mutants = fromStryker({
      files: {
        'src/a.ts': {
          mutants: ['Ignored', 'CompileError', 'RuntimeError'].map((status) =>
            strykerMutant(status),
          ),
        },
      },
    })

    assert.deepEqual(mutants, [])
  })
})

describe('fromPitest', () => {
  it('reads each mutation, naming it by class and method', () => {
    const mutants = fromPitest(
      `<mutations>\n${pitestMutant('KILLED')}\n${pitestMutant('TIMED_OUT')}\n${pitestMutant('SURVIVED', 12)}\n${pitestMutant('NO_COVERAGE')}\n</mutations>`,
    )

    assert.deepEqual(
      mutants.map(({ detected }) => detected),
      [true, true, false, false],
    )
    assert.deepEqual(mutants[2], {
      detected: false,
      file: 'com.skill2career.domain.Profile.amend',
      line: 12,
      mutator: 'VoidMethodCallMutator',
      change: 'removed call to setHeadline',
    })
  })

  it('leaves out mutants that could not be loaded', () => {
    assert.deepEqual(fromPitest(`<mutations>${pitestMutant('NON_VIABLE')}</mutations>`), [])
  })

  it("unescapes the xml's text, so a change reads as the code would", () => {
    const [mutant] = fromPitest(
      pitestMutant('SURVIVED').replace(
        'removed call to setHeadline',
        'replaced return value with &quot;&quot; for &lt;init&gt; &amp; &apos;x&apos;',
      ),
    )

    assert.equal(mutant.change, `replaced return value with "" for <init> & 'x'`)
  })
})

describe('fromReports', () => {
  it("reads every module's pitest report as one, as the api is scored across its modules", () => {
    const mutants = fromReports('pitest', [
      `<mutations>${pitestMutant('KILLED', 1)}</mutations>`,
      `<mutations>${pitestMutant('SURVIVED', 2)}</mutations>`,
    ])

    assert.deepEqual(
      mutants.map(({ detected, line }) => [detected, line]),
      [
        [true, 1],
        [false, 2],
      ],
    )
  })

  it("parses stryker's reports as json", () => {
    const report = (status) =>
      JSON.stringify({
        files: { 'src/a.ts': { mutants: [strykerMutant(status)] } },
      })

    assert.equal(fromReports('stryker', [report('Killed'), report('Survived')]).length, 2)
  })
})

describe('summary', () => {
  const mutant = (detected, line) => ({
    detected,
    file: 'src/a.ts',
    line,
    mutator: 'StringLiteral',
    change: '""',
  })

  it('gives the score, and a row for each mutant that survived', () => {
    const text = summary('packages/auth', [
      mutant(true, 1),
      mutant(true, 2),
      mutant(true, 3),
      mutant(false, 4),
    ])

    assert.match(text, /### Mutation testing: packages\/auth/)
    assert.match(text, /Score 75\.0%: 3 of 4 mutants caught/)
    assert.match(text, /\| src\/a\.ts \| 4 \| StringLiteral \| "" \|/)
    assert.doesNotMatch(text, /\| 1 \|/)
  })

  it('shows the first 50 survivors, and says how many more are in the report', () => {
    const text = summary(
      'api',
      Array.from({ length: 60 }, (_, line) => mutant(false, line)),
    )

    assert.equal(text.match(/^\| src/gm).length, 50)
    assert.match(text, /And 10 more, in the report\./)
  })

  it('keeps a change with a pipe in it inside its cell', () => {
    const text = summary('ui', [{ ...mutant(false, 1), change: 'a || b' }])

    assert.match(text, /a \\\|\\\| b/)
  })

  it('has no score when there was nothing to mutate', () => {
    assert.match(summary('shell', []), /Score -: 0 of 0/)
  })
})
