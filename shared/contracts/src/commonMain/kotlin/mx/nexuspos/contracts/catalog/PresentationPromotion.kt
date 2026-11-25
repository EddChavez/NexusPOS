package mx.nexuspos.contracts.catalog

import kotlinx.serialization.Serializable

@Serializable
data class PairPromotionSchedule(val secondUnitPercent: Int, val fromDate: String, val throughDate: String)

@Serializable
data class PresentationPromotion(val schedule: PairPromotionSchedule, val startsAt: Long, val endsAtExclusive: Long)

@Serializable
data class UpdatePresentationPromotionRequest(val schedule: PairPromotionSchedule? = null)

fun presentationPromotionDiscount(
    promotion: PresentationPromotion?,
    now: Long,
    quantityMillis: Long,
    publicPriceCents: Long,
    resolvedPriceCents: Long
): Long {
    if (promotion == null || now !in promotion.startsAt until promotion.endsAtExclusive ||
        quantityMillis < 2000 || quantityMillis % 1000 != 0L || publicPriceCents <= 0L || resolvedPriceCents <= 0L
    ) return 0
    val units = quantityMillis / 1000L
    if (publicPriceCents > Long.MAX_VALUE / units || resolvedPriceCents > Long.MAX_VALUE / units) return 0
    val percent = promotion.schedule.secondUnitPercent.coerceIn(0, 100)
    val discountPerPair = publicPriceCents / 100 * percent + (publicPriceCents % 100 * percent + 50) / 100
    val promotionalTotal = publicPriceCents * units - discountPerPair * (units / 2)
    return (resolvedPriceCents * units - promotionalTotal).coerceAtLeast(0)
}
