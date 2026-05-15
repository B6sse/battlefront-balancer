import { fetchApi } from './client'
import type { PlayerWithStats, PlayerMatchHistoryEntry } from '../types'

export function getPlayers(): Promise<PlayerWithStats[]> {
  return fetchApi<PlayerWithStats[]>('/players')
}

export function getPlayersWithSeason(season?: string): Promise<PlayerWithStats[]> {
  const query = season ? `?season=${encodeURIComponent(season)}` : ''
  return fetchApi<PlayerWithStats[]>(`/players${query}`)
}

export function getPlayerMatchHistory(
  playerId: number,
  season?: string,
): Promise<PlayerMatchHistoryEntry[]> {
  const query = season ? `?season=${encodeURIComponent(season)}` : ''
  return fetchApi<PlayerMatchHistoryEntry[]>(`/players/${playerId}/matches${query}`)
}
