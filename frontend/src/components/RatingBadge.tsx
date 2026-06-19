function getRatingTier(rating: number): string {
  if (rating >= 82) return 'rare-gold'
  if (rating >= 77) return 'gold'
  if (rating >= 71) return 'rare-silver'
  if (rating >= 63) return 'silver'
  if (rating >= 56) return 'rare-bronze'
  return 'bronze'
}

export function RatingBadge({ rating }: { rating: number }) {
  const tier = getRatingTier(rating)
  return <span className={`rating-badge rating-badge--${tier}`}>{rating}</span>
}
