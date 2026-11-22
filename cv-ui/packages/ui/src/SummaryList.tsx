import styles from './SummaryList.module.css'

type Row = { key: string; value: string | undefined | null }

/** Labels and their values, as a description list. */
export function SummaryList({ rows }: { rows: Row[] }) {
  return (
    <dl className={styles.list}>
      {rows.map(({ key, value }) => (
        <div key={key} className={styles.row}>
          <dt className={styles.key}>{key}</dt>
          <dd className={value ? styles.value : `${styles.value} ${styles.missing}`}>
            {value || 'Not given'}
          </dd>
        </div>
      ))}
    </dl>
  )
}
