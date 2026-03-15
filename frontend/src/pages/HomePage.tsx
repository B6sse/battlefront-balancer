import { useHealth } from '../hooks/useHealth'
import styles from '../styles/HomePage.module.scss'

export function HomePage() {
  const { health, loading, error } = useHealth()

  return (
    <main className={styles.page}>
      <h1>Battlefront Balancer</h1>
      <p className={styles.tagline}>
        Rangert spill og rating for balanserte kamper i Star Wars Battlefront 2015.
      </p>
      <section className={styles.backendStatus}>
        <h2>Backend</h2>
        {loading && <p>Sjekker …</p>}
        {error && <p className={styles.error}>Feil: {error.message}</p>}
        {health && <p className={styles.ok}>Status: {health.status}</p>}
      </section>
    </main>
  )
}
