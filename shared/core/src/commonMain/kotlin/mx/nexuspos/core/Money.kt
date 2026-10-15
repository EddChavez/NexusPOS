package mx.nexuspos.core

import kotlin.jvm.JvmInline
import kotlin.math.abs
import kotlin.math.roundToLong

@JvmInline
value class Money(val cents: Long) : Comparable<Money> {
    val pesos: Double get() = cents / 100.0

    operator fun plus(other: Money): Money = Money(cents + other.cents)
    operator fun minus(other: Money): Money = Money(cents - other.cents)
    operator fun times(factor: Long): Money = Money(cents * factor)
    operator fun div(divisor: Long): Money = Money(cents / divisor)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    fun isZero(): Boolean = cents == 0L
    fun isPositive(): Boolean = cents > 0L
    fun isNegative(): Boolean = cents < 0L

    fun format(): String {
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        val whole = absolute / 100
        val fraction = (absolute % 100).toString().padStart(2, '0')
        return "$sign$$whole.$fraction"
    }

    override fun toString(): String = format()

    companion object {
        val ZERO = Money(0L)
        fun pesos(amount: Double): Money = Money((amount * 100.0).roundToLong())
        fun fromCents(cents: Long): Money = Money(cents)
    }
}
