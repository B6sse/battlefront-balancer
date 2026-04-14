// Fellestypedefinisjoner for frontend

export interface MatchSummary {
  id: number
  date: string
  map: string
  rule: string
  teamSize: number
  mvpName: string | null
  supervisorName: string | null
}

export interface MatchPlayerStat {
  nickname: string
  nation: string
  score: number
  updateBr: number
  newBr: number
  perf: number
}

export interface MatchDetail {
  id: number
  rebelScore: number
  imperialScore: number
  teamSize: number
  rebels: MatchPlayerStat[]
  imperials: MatchPlayerStat[]
}


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
