import { fetchApi } from './client'
import type { MatchDetail, MatchSummary } from '../types'

export function getSeasons(): Promise<number[]> {
  return fetchApi<number[]>('/seasons')
}

export function getMatchList(season?: string): Promise<MatchSummary[]> {
  const query = season ? `?season=${season}` : ''
  return fetchApi<MatchSummary[]>(`/matches${query}`)
}

export function getMatchDetail(id: number, season?: string): Promise<MatchDetail> {
  const query = season ? `?season=${season}` : ''
  return fetchApi<MatchDetail>(`/matches/${id}${query}`)
}
