package mx.nexuspos.core

import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlin.math.roundToLong

@JvmInline
value class Quantity(val millis: Long) : Comparable<Quantity> {
    val toDouble: Double get() = millis / 1000.0

    operator fun plus(other: Quantity): Quantity = Quantity(millis + other.millis)
    operator fun minus(other: Quantity): Quantity = Quantity(millis - other.millis)
    operator fun times(factor: Long): Quantity = Quantity(millis * factor)
    operator fun div(divisor: Long): Quantity = Quantity(millis / divisor)

    override fun compareTo(other: Quantity): Int = millis.compareTo(other.millis)

    fun isZero(): Boolean = millis == 0L
    fun isPositive(): Boolean = millis > 0L
    fun isNegative(): Boolean = millis < 0L

    fun format(unit: ProductUnit = ProductUnit.PIECE): String {
        val sign = if (millis < 0) "-" else ""
        val absolute = abs(millis)
        return when (unit) {
            ProductUnit.PIECE -> {
                if (absolute % 1000L == 0L) {
                    "$sign${absolute / 1000L} pza"
                } else {
                    val whole = absolute / 1000L
                    val decimals = (absolute % 1000L).toString().padStart(3, '0').trimEnd('0')
                    "$sign$whole.$decimals pza"
                }
            }
            ProductUnit.KILOGRAM -> {
                val whole = absolute / 1000L
                val decimals = (absolute % 1000L).toString().padStart(3, '0')
                "$sign$whole.$decimals kg"
            }
            ProductUnit.LITER -> {
                val whole = absolute / 1000L
                val decimals = (absolute % 1000L).toString().padStart(3, '0')
                "$sign$whole.$decimals L"
            }
        }
    }

    override fun toString(): String = format()

    companion object {
        val ZERO = Quantity(0L)
        val ONE = Quantity(1000L)
        fun pieces(count: Long): Quantity = Quantity(count * 1000L)
        fun fromDouble(amount: Double): Quantity = Quantity((amount * 1000.0).roundToLong())
        fun fromMillis(millis: Long): Quantity = Quantity(millis)
    }
}
