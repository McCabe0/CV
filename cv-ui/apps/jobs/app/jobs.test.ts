import { LOCATION_MAX_LENGTH, readFilter, skillsLine, workplaceLabel } from './jobs'

describe('readFilter', () => {
  it('is every job, anywhere, when nothing is asked for', () => {
    expect(readFilter({})).toEqual({ remoteOnly: false, location: '' })
  })

  it('reads remote only from its ticked box, and the place, trimmed', () => {
    expect(readFilter({ remoteOnly: 'on', location: '  London ' })).toEqual({
      remoteOnly: true,
      location: 'London',
    })
  })

  it('takes the first of a value given twice, as a url can carry', () => {
    expect(readFilter({ location: ['UK', 'Spain'] })).toEqual({ remoteOnly: false, location: 'UK' })
  })

  it('cuts a place longer than the api takes, rather than failing the page', () => {
    expect(readFilter({ location: 'x'.repeat(200) }).location).toHaveLength(LOCATION_MAX_LENGTH)
  })
})

describe('workplaceLabel', () => {
  it('says where the job is done, in plain words', () => {
    expect(workplaceLabel('REMOTE')).toBe('Remote')
    expect(workplaceLabel('HYBRID')).toBe('Hybrid')
    expect(workplaceLabel('ONSITE')).toBe('On site')
  })

  it('says nothing when the board did not say', () => {
    expect(workplaceLabel(undefined)).toBeUndefined()
  })
})

describe('skillsLine', () => {
  it('lists the skills the job asks for, as a sentence', () => {
    expect(skillsLine(['Kotlin'])).toBe('Asks for Kotlin')
    expect(skillsLine(['Kotlin', 'Kafka'])).toBe('Asks for Kotlin and Kafka')
    expect(skillsLine(['Kotlin', 'Kafka', 'PostgreSQL'])).toBe(
      'Asks for Kotlin, Kafka and PostgreSQL',
    )
  })
})
