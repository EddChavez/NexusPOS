package mx.nexuspos.server.platform

import mx.nexuspos.contracts.ApiError

open class AppException(
    val code: String,
    override val message: String,
    val details: Map<String, String> = emptyMap()
) : RuntimeException(message)

class ValidationException(message: String, details: Map<String, String> = emptyMap()) :
    AppException("VALIDATION_ERROR", message, details)

class NotFoundException(message: String) :
    AppException("NOT_FOUND", message)

class PriceChangedException(message: String) :
    AppException("PRICE_CHANGED", message)

class InsufficientStockException(message: String) :
    AppException("INSUFFICIENT_STOCK", message)

class CreditLimitExceededException(message: String) :
    AppException("CREDIT_LIMIT_EXCEEDED", message)

class SessionClosedException(message: String) :
    AppException("SESSION_CLOSED", message)

class UnauthorizedException(message: String) :
    AppException("UNAUTHENTICATED", message)

class DesktopSessionInvalidException(message: String) :
    AppException(ApiError.DESKTOP_SESSION_INVALID, message)

class ForbiddenException(message: String) :
    AppException("PERMISSION_DENIED", message)

class IdempotencyMismatchException(message: String) :
    AppException("IDEMPOTENCY_MISMATCH", message)


class StockChangedException(message: String) :
    AppException("STOCK_CHANGED", message)

class DuplicatePurchaseDocumentException(message: String) :
    AppException("PURCHASE_DOCUMENT_EXISTS", message)

class ConflictException(message: String) : AppException("CONFLICT", message)

internal class PinAttemptLimitException : AppException("RATE_LIMITED", "Demasiados intentos de PIN. Espera un minuto.")

internal class PinAttemptWindow {
    private val attempts = LinkedHashMap<String, Pair<Long, Int>>()

    @Synchronized
    fun check(scope: String) {
        val now = System.currentTimeMillis()
        attempts.entries.removeIf { now - it.value.first >= 60_000 }
        val window = attempts[scope] ?: (now to 0)
        if (window.second >= 18) throw PinAttemptLimitException()
        attempts[scope] = window.first to window.second + 1
    }
}
