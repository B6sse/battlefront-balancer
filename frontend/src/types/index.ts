// Fellestypedefinisjoner for frontend

export interface HealthStatus {
  status: string
}

/** Player with current-season stats (GET /api/players). Matches backend PlayerWithStatsDto. */
export interface PlayerWithStats {
  id: number
  nickname: string
  nation: string
  rating: number
  dzrating: number
  elo: number
  br: number
  played: number
  best: number
  won: number
  lost: number
  draw: number
  score: number
  mvp: number
}
