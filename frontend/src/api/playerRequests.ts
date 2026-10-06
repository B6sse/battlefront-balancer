import { fetchApi } from './client'

/** A pending request from an Auric host to register a player. */
export interface PlayerRequestDto {
  id: number
  personaId: number
  nickname: string
  nation: string
  rating: number
  inGameName: string | null
  status: string
  requestedBy: string | null
  requestedAt: string
}

export interface PlayerRequestApproval {
  nickname: string
  nation: string
  rating: number
}

export function getPlayerRequests(): Promise<PlayerRequestDto[]> {
  return fetchApi<PlayerRequestDto[]>('/player-requests')
}

export function approvePlayerRequest(id: number, data: PlayerRequestApproval): Promise<{ playerId: number }> {
  return fetchApi<{ playerId: number }>(`/player-requests/${id}/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

export function linkPlayerRequest(id: number, playerId: number): Promise<{ playerId: number }> {
  return fetchApi<{ playerId: number }>(`/player-requests/${id}/link`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ playerId }),
  })
}

export function rejectPlayerRequest(id: number): Promise<void> {
  return fetchApi<void>(`/player-requests/${id}/reject`, { method: 'POST' })
}
