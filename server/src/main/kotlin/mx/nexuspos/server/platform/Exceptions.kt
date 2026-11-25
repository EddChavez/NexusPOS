package mx.nexuspos.server.platform

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

class ForbiddenException(message: String) :
    AppException("PERMISSION_DENIED", message)

class IdempotencyMismatchException(message: String) :
    AppException("IDEMPOTENCY_MISMATCH", message)


class StockChangedException(message: String) :
    AppException("STOCK_CHANGED", message)
