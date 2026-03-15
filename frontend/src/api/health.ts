import { fetchApi } from './client'
import type { HealthStatus } from '../types'

export function getHealth(): Promise<HealthStatus> {
  return fetchApi<HealthStatus>('/health')
}
