const API_BASE = '/api'

export interface RandomizerResult {
  map: string
  rule: string
}

export interface PlayerMatchStat {
  id: number
  faction: string
  outcome: string
  score: number
  perf: number
  change: number
  NewBR: number
  kills: number
  deaths: number
}

export interface MatchSubmitPayload {
  matchData: [string, number, number, number, number, string]
  rebels: PlayerMatchStat[]
  imperials: PlayerMatchStat[]
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

export interface SubmitMatchResult {
  nextMap: string
  nextRule: string
}

export async function submitMatch(payload: MatchSubmitPayload): Promise<SubmitMatchResult> {
  const res = await fetch(`${API_BASE}/matches`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(payload),
  })
  const data = await res.json()
  if (!data.success) throw new Error(data.message || 'Failed to submit match')
  return { nextMap: data.nextMap, nextRule: data.nextRule }
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
