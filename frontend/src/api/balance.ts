/** Which rating to balance on: season BR (ranked), manual rating (intern) or Drop Zone rating. */
export type BalanceRatingKey = 'br' | 'rating' | 'dzrating'

export interface BalanceResult {
  /** Player ids, highest rating first */
  rebels: number[]
  imperials: number[]
  rebelAverage: number
  imperialAverage: number
}

/** Splits the given players into two balanced teams on the server (same algorithm Auric uses). */
export async function balanceTeams(ratingKey: BalanceRatingKey, playerIds: number[]): Promise<BalanceResult> {
  const res = await fetch('/api/balance', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ratingKey, playerIds }),
  })
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new Error(data?.message ?? 'Failed to balance teams')
  return data as BalanceResult
}
