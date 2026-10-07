const API_BASE = '/api'

export interface RandomizerResult {
  map: string
  rule: string
}

export interface LastMatchPlayer {
  id: number
  nickname: string
  nation: string
  rating: number
  br: number
  best: number
  played: number
  won: number
  lost: number
  draw: number
  score: number
  mvp: number
  personaId: number | null
  lastSeenName: string | null
}

export async function getRandomizer(): Promise<RandomizerResult> {
  const res = await fetch(`${API_BASE}/randomizer`)
  if (!res.ok) throw new Error('Failed to load randomizer')
  return res.json()
}

export async function postRandomizer(map: string, rule: string): Promise<void> {
  const res = await fetch(`${API_BASE}/randomizer`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ map, rule }),
  })
  if (!res.ok) throw new Error('Failed to save randomizer')
}

export async function getLastMatch(): Promise<LastMatchPlayer[]> {
  const res = await fetch(`${API_BASE}/last-match`)
  if (!res.ok) throw new Error('Failed to load last match')
  return res.json()
}

export interface RawMatchPlayerInput {
  playerId: number
  faction: 'Rebel' | 'Imperial'
  score: number
  kills: number
  deaths: number
  partnerless: boolean
}

/** Raw result of one map; the server computes BR, perf and MVP. */
export interface RawMatchInput {
  map: string
  rule: string
  rebelScore: number
  imperialScore: number
  players: RawMatchPlayerInput[]
}

export interface RawMatchPlayerResult {
  playerId: number
  personaId: number | null
  nickname: string
  outcome: 'Won' | 'Lost' | 'Draw'
  score: number
  perf: number
  change: number
  newBr: number
}

export interface RawMatchResult {
  /** null for a dry run */
  matchId: number | null
  map: string
  rule: string
  mvpPlayerId: number | null
  /** Same order as the players were sent, per team */
  rebels: RawMatchPlayerResult[]
  imperials: RawMatchPlayerResult[]
  nextMap: string | null
  nextRule: string | null
}

/**
 * Rates a match on the server. With [dryRun] the result is only computed (to preview BR changes);
 * otherwise the match is stored and the randomizer picks the next map and rule.
 */
export async function rateMatch(input: RawMatchInput, dryRun: boolean): Promise<RawMatchResult> {
  const res = await fetch(`${API_BASE}/matches/raw${dryRun ? '?dryRun=true' : ''}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(input),
  })
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new Error(data?.message ?? 'Failed to submit match')
  return data as RawMatchResult
}

export interface RandomizerWeightEntry {
  id: number
  type: string
  name: string
  weight: number
}

export interface RandomizerWeightsResponse {
  maps: RandomizerWeightEntry[]
  rules: RandomizerWeightEntry[]
}

export async function getRandomizerWeights(): Promise<RandomizerWeightsResponse> {
  const res = await fetch(`${API_BASE}/randomizer/weights`, { credentials: 'include' })
  if (!res.ok) throw new Error('Failed to load randomizer weights')
  return res.json()
}

export async function updateRandomizerWeights(
  weights: { id: number; weight: number }[],
): Promise<RandomizerWeightsResponse> {
  const res = await fetch(`${API_BASE}/randomizer/weights`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ weights }),
  })
  if (!res.ok) throw new Error('Failed to update weights')
  return res.json()
}
