import styles from '../styles/Layout.module.scss'

interface LayoutProps {
  children: React.ReactNode
}

export function Layout({ children }: LayoutProps) {
  return <div className={styles.layout}>{children}</div>
}
