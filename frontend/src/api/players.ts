import { fetchApi } from './client'
import type { PlayerWithStats } from '../types'

export function getPlayers(): Promise<PlayerWithStats[]> {
  return fetchApi<PlayerWithStats[]>('/players')
}
