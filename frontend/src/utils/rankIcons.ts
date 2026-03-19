/** Rating ranges and rank distribution from Old_Project script.js */

const RATING_RANGES: Record<string, { min: number; max: number; icon: string }> = {
  kyber: { min: 87, max: 99, icon: 'kyber' },
  beskar: { min: 82, max: 86, icon: 'beskar' },
  diamond: { min: 77, max: 81, icon: 'diamond' },
  platinum: { min: 72, max: 76, icon: 'platinum' },
  aurodium: { min: 67, max: 71, icon: 'aurodium' },
  chromium: { min: 62, max: 66, icon: 'chromium' },
  bronzium: { min: 0, max: 61, icon: 'bronzium' },
}

const RANK_DISTRIBUTION: Record<string, number> = {
  kyber: 0.075,
  beskar: 0.15,
  diamond: 0.25,
  platinum: 0.4,
  aurodium: 0.55,
  chromium: 0.75,
  bronzium: 1.0,
}

/** Returns rank icon filename for a numeric rating (Intern view). */
export function getRankIconName(rating: number): string | null {
  for (const range of Object.values(RATING_RANGES)) {
    if (rating >= range.min && rating <= range.max) {
      return range.icon
    }
  }
  return null
}

/** Returns rank icon filename by distribution fraction (Ranked view, 0–1). */
export function getRankByDistributionFraction(rankFraction: number): string | null {
  if (rankFraction <= 0) return null
  for (const [rank, threshold] of Object.entries(RANK_DISTRIBUTION)) {
    if (rankFraction <= threshold) {
      return RATING_RANGES[rank].icon
    }
  }
  return null
}
