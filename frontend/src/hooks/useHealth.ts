import { useState, useEffect } from 'react'
import { getHealth } from '../api/health'
import type { HealthStatus } from '../types'

export function useHealth(): { health: HealthStatus | null; loading: boolean; error: Error | null } {
  const [health, setHealth] = useState<HealthStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    getHealth()
      .then(setHealth)
      .catch(setError)
      .finally(() => setLoading(false))
  }, [])

  return { health, loading, error }
}
