import { fetchApi } from './client'
import type { PlayerWithStats, PlayerMatchHistoryEntry } from '../types'

export interface PlayerCreateRequest {
  nickname: string
  nation: string
  rating: number
  personaId: number | null
}

export interface PlayerUpdateRequest {
  nickname: string
  nation: string
  rating: number
  dzrating: number
  br: number
  personaId: number | null
}

export function getPlayers(): Promise<PlayerWithStats[]> {
  return fetchApi<PlayerWithStats[]>('/players')
}

export function getInternPlayers(): Promise<PlayerWithStats[]> {
  return fetchApi<PlayerWithStats[]>('/players?view=intern')
}

export function getPlayersWithSeason(season?: string): Promise<PlayerWithStats[]> {
  const query = season ? `?season=${encodeURIComponent(season)}` : ''
  return fetchApi<PlayerWithStats[]>(`/players${query}`)
}

export function createPlayer(data: PlayerCreateRequest): Promise<PlayerWithStats> {
  return fetchApi<PlayerWithStats>('/players', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

export function updatePlayer(id: number, data: PlayerUpdateRequest): Promise<PlayerWithStats> {
  return fetchApi<PlayerWithStats>(`/players/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

export function deletePlayer(id: number): Promise<void> {
  return fetchApi<void>(`/players/${id}`, { method: 'DELETE' })
}

export function getPlayerMatchHistory(
  playerId: number,
  season?: string,
): Promise<PlayerMatchHistoryEntry[]> {
  const query = season ? `?season=${encodeURIComponent(season)}` : ''
  return fetchApi<PlayerMatchHistoryEntry[]>(`/players/${playerId}/matches${query}`)
}
