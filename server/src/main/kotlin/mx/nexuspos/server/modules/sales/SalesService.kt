package mx.nexuspos.server.modules.sales

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.ClosedShiftSaleDetail
import mx.nexuspos.contracts.cash.ClosedShiftSaleRow
import mx.nexuspos.contracts.cash.ClosedShiftSalesPage
import mx.nexuspos.contracts.cash.HistoricalSaleCancellation
import mx.nexuspos.contracts.catalog.KitComponent
import mx.nexuspos.contracts.catalog.resolvePresentationPrice
import mx.nexuspos.contracts.loyalty.SaleLoyaltyRewardDto
import mx.nexuspos.contracts.sales.*
import mx.nexuspos.core.Money
import mx.nexuspos.core.PricingRules
import mx.nexuspos.core.Quantity
import mx.nexuspos.server.modules.system.SettingsService
import mx.nexuspos.server.modules.cash.requireOperator
import mx.nexuspos.server.modules.cash.CashSessionService
import mx.nexuspos.server.modules.permissions.permissionsForRole
import mx.nexuspos.server.modules.permissions.writeAudit
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.*
import java.sql.Connection
import java.util.UUID
import kotlin.math.roundToLong

class SalesService(
    private val db: DatabaseFactory,
    private val settingsService: SettingsService
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun completeSale(request: CreateSaleRequest, verifyOperator: Boolean = false, drawerApproval: String? = null): SaleResponse {
        return db.inTransaction { conn ->
            val requestHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(json.encodeToString(request).toByteArray())
                .let(java.util.HexFormat.of()::formatHex)
            val claimed = conn.prepareStatement(
                "INSERT INTO idempotency_keys (key_id, request_hash, response_body, created_at) VALUES (?, ?, '', ?) ON CONFLICT DO NOTHING"
            ).use { statement ->
                statement.setString(1, request.idempotencyKey)
                statement.setString(2, requestHash)
                statement.setLong(3, System.currentTimeMillis())
                statement.executeUpdate() == 1
            }
            if (!claimed) {
                return@inTransaction conn.prepareStatement(
                    "SELECT request_hash, response_body FROM idempotency_keys WHERE key_id = ?"
                ).use { statement ->
                    statement.setString(1, request.idempotencyKey)
                    statement.executeQuery().use { result ->
                        check(result.next()) { "No se encontró el resultado del cobro previo." }
                        val savedHash = result.getString("request_hash")
                        if (savedHash != "hash" && savedHash != requestHash) {
                            throw IdempotencyMismatchException("La clave de este cobro ya se usó con otros datos.")
                        }
                        json.decodeFromString<SaleResponse>(result.getString("response_body"))
                    }
                }
            }

            if (verifyOperator) requireOperator(conn, request.cashSessionId, request.cashierUserId)

            // 2. Validate Session is OPEN
            val sessStmt = conn.prepareStatement("SELECT status FROM cash_sessions WHERE id = ? FOR UPDATE")
            sessStmt.setString(1, request.cashSessionId)
            val sessRs = sessStmt.executeQuery()
            if (!sessRs.next()) {
                sessRs.close()
                sessStmt.close()
                throw NotFoundException("Sesión de caja '${request.cashSessionId}' no encontrada")
            }
            val sessionStatus = sessRs.getString("status")
            sessRs.close()
            sessStmt.close()
            if (sessionStatus != "OPEN") {
                throw SessionClosedException("La sesión de caja está cerrada. No se pueden procesar ventas.")
            }

            val cashIncrease = request.payments.filter { it.method == "CASH" }.sumOf { it.amountCents }
            CashSessionService(db).enforceDrawerLimit(conn, request.cashSessionId, cashIncrease, drawerApproval, request.cashierUserId)

            // 3. Fetch Settings for Negative Stock Policy
            val settings = settingsService.getSettings()
            if (!settings.commerce.quickSaleEnabled && request.lines.any { it.productId == null && it.presentationId == null }) {
                throw ForbiddenException("La venta rápida está deshabilitada en esta sucursal.")
            }
            if (!settings.commerce.creditSalesEnabled && request.payments.any { it.method.equals("CREDIT", ignoreCase = true) }) {
                throw ForbiddenException("Las nuevas ventas a crédito están deshabilitadas.")
            }

            // 4. Validate lines and calculate exact totals
            var calculatedSubtotalCents = 0L
            var calculatedTaxCents = 0L

            data class ValidatedLine(
                val productId: String?,
                val presentationId: String?,
                val description: String,
                val quantityMillis: Long,
                val unitPriceCents: Long,
                val taxRate: Double,
                val taxCents: Long,
                val totalCents: Long,
                val discountCents: Long,
                val discountPercent: Double,
                val kitStock: List<KitComponent>,
                val stock: List<KitComponent>,
                val factorMillis: Long,
                val priceListLevel: Int?,
                val manualPriceOverride: Boolean
            )

            val validatedLines = mutableListOf<ValidatedLine>()

            for (line in request.lines) {
                if (line.quantityMillis <= 0L) throw ValidationException("La cantidad de venta debe ser positiva.")
                var isKit = false
                var currentPriceCents = line.expectedUnitPriceCents
                var taxRate = line.taxRate
                var factorMillis = 1000L
                var usesInventory = false
                var resolvedProdId = line.productId

                if (line.priceListLevel != null && line.presentationId.isNullOrBlank()) {
                    throw ValidationException("Selecciona una presentación para aplicar la lista de precios.")
                }
                if (!line.presentationId.isNullOrBlank()) {
                    val presStmt = conn.prepareStatement(
                        """
                        SELECT pp.price_cents, pp.price_tiers_json, pp.price_list_prices, pp.factor_millis, pp.product_id, p.tax_rate, p.uses_inventory,
                            EXISTS(SELECT 1 FROM product_kit_components k WHERE k.kit_product_id = p.id) AS is_kit
                        FROM product_presentations pp
                        JOIN products p ON pp.product_id = p.id
                        WHERE pp.id = ? AND pp.is_active = TRUE AND p.is_active = TRUE
                        """.trimIndent()
                    )
                    presStmt.setString(1, line.presentationId)
                    val presRs = presStmt.executeQuery()
                    if (presRs.next()) {
                        val selectedList = line.priceListLevel
                        currentPriceCents = if (selectedList != null) {
                            val enabled = conn.prepareStatement("SELECT enabled FROM price_lists WHERE level_id = ? FOR UPDATE").use {
                                it.setInt(1, selectedList)
                                it.executeQuery().use { row -> row.next() && row.getBoolean(1) }
                            }
                            val prices = json.decodeFromString<Map<Int, Long>>(presRs.getString("price_list_prices"))
                            prices[selectedList]?.takeIf { enabled }
                                ?: throw PriceChangedException("La lista de precios ya no está disponible para '${line.description}'.")
                        } else resolvePresentationPrice(
                            presRs.getLong("price_cents"),
                            json.decodeFromString(presRs.getString("price_tiers_json")),
                            line.quantityMillis
                        )
                        factorMillis = presRs.getLong("factor_millis")
                        resolvedProdId = presRs.getString("product_id")
                        taxRate = presRs.getDouble("tax_rate")
                        usesInventory = presRs.getBoolean("uses_inventory")
                        isKit = presRs.getBoolean("is_kit")
                    } else {
                        presRs.close()
                        presStmt.close()
                        throw NotFoundException("No se encontró la presentación de '${line.description}'.")
                    }
                    presRs.close()
                    presStmt.close()
                } else if (!line.productId.isNullOrBlank()) {
                    val pStmt = conn.prepareStatement("""
                        SELECT tax_rate, uses_inventory,
                               EXISTS(SELECT 1 FROM product_kit_components k WHERE k.kit_product_id = p.id) AS is_kit
                        FROM products p WHERE id = ?
                    """.trimIndent())
                    pStmt.setString(1, line.productId)
                    val pRs = pStmt.executeQuery()
                    if (pRs.next()) {
                        taxRate = pRs.getDouble("tax_rate")
                        usesInventory = pRs.getBoolean("uses_inventory")
                        isKit = pRs.getBoolean("is_kit")
                    }
                    pRs.close()
                    pStmt.close()
                }

                if (line.manualPriceCents != null) {
                    val manual = line.manualPriceCents ?: error("Precio manual ausente.")
                    if (line.priceListLevel != null || manual < 0 || (line.productId == null && line.presentationId == null)) {
                        throw ValidationException("El precio manual requiere un artículo de catálogo y no se combina con una lista.")
                    }
                    currentPriceCents = manual
                }

                // RN-VEN-02: Validate current price
                if (currentPriceCents != line.expectedUnitPriceCents) {
                    throw PriceChangedException(
                        "El precio de '${line.description}' ha cambiado a $${currentPriceCents / 100.0} (esperado: $${line.expectedUnitPriceCents / 100.0})"
                    )
                }

                val (lineTotal, lineDisc) = PricingRules.calculateLineTotalWithDiscount(
                    Money.fromCents(currentPriceCents),
                    Quantity.fromMillis(line.quantityMillis),
                    line.discountPercent,
                    line.discountCents
                )
                val lineTax = PricingRules.extractTaxFromTotal(lineTotal, taxRate)

                calculatedSubtotalCents += (lineTotal.cents - lineTax.cents)
                calculatedTaxCents += lineTax.cents

                // Validate discount against maxCashierDiscountPercent if exceeding threshold
                if (line.discountPercent > settings.maxCashierDiscountPercent) {
                    val supPin = request.supervisorAuthPin
                    if (supPin.isNullOrBlank()) {
                        throw ValidationException("El descuento del ${line.discountPercent}% excede el límite permitido sin supervisor (${settings.maxCashierDiscountPercent}%). Se requiere autorización de supervisor.")
                    }
                    val supStmt = conn.prepareStatement(
                        "SELECT name, pin_hash FROM users WHERE role IN ('ADMIN', 'SUPERVISOR') AND is_active = TRUE"
                    )
                    val supRs = supStmt.executeQuery()
                    var authorizedSupervisor: String? = null
                    while (supRs.next()) {
                        val hash = supRs.getString("pin_hash")
                        if (Security.verifyPin(supPin, hash)) {
                            authorizedSupervisor = supRs.getString("name")
                            break
                        }
                    }
                    supRs.close()
                    supStmt.close()

                    if (authorizedSupervisor == null) {
                        throw ForbiddenException("PIN de supervisor inválido o no autorizado para autorizar descuento")
                    }
                }

                val definition = if (isKit) loadKitStock(conn, checkNotNull(resolvedProdId)) else emptyList()
                if ((isKit && definition.isEmpty()) ||
                    line.expectedKitComponents?.sortedBy { it.productId }?.let { it != definition } == true
                ) throw PriceChangedException("La composición de '${line.description}' cambió. Consulta el catálogo antes de cobrar.")
                if (isKit && (line.presentationId.isNullOrBlank() || line.quantityMillis % 1000 != 0L ||
                        factorMillis <= 0 || factorMillis % 1000 != 0L)
                ) throw ValidationException("Los kits requieren una presentación y una cantidad entera.")
                val components = definition.map { component ->
                    component.copy(quantityMillis = multiplyStock(component.quantityMillis, factorMillis / 1000L))
                }
                val stock = when {
                    isKit -> components.map { component ->
                        component.copy(quantityMillis = multiplyStock(component.quantityMillis, line.quantityMillis / 1000L))
                    }
                    usesInventory && resolvedProdId != null -> listOf(
                        KitComponent(resolvedProdId, multiplyStock(line.quantityMillis, factorMillis) / 1000L)
                    )
                    else -> emptyList()
                }

                validatedLines.add(
                    ValidatedLine(
                        productId = resolvedProdId,
                        presentationId = line.presentationId,
                        description = line.description,
                        quantityMillis = line.quantityMillis,
                        unitPriceCents = currentPriceCents,
                        taxRate = taxRate,
                        taxCents = lineTax.cents,
                        totalCents = lineTotal.cents,
                        discountCents = lineDisc.cents,
                        discountPercent = line.discountPercent,
                        kitStock = components,
                        stock = stock,
                        factorMillis = factorMillis,
                        priceListLevel = line.priceListLevel,
                        manualPriceOverride = line.manualPriceCents != null
                    )
                )
            }

            val calculatedTotalCents = calculatedSubtotalCents + calculatedTaxCents
            if (calculatedTotalCents != request.expectedTotalCents) {
                throw PriceChangedException(
                    "El total esperado ($${request.expectedTotalCents / 100.0}) no coincide con el total recalculado por el servidor ($${calculatedTotalCents / 100.0})"
                )
            }

            val saleStock = summarizeStock(validatedLines.flatMap { it.stock })
            conn.prepareStatement("SELECT quantity_millis FROM stock_balances WHERE product_id = ? FOR UPDATE").use { statement ->
                for ((productId, required) in saleStock) {
                    statement.setString(1, productId)
                    val available = statement.executeQuery().use { rows ->
                        if (rows.next()) rows.getLong(1) else throw ValidationException("El producto $productId no tiene inventario.")
                    }
                    if (!settings.allowNegativeStock && available < required) {
                        val name = conn.prepareStatement("SELECT name FROM products WHERE id = ?").use { names ->
                            names.setString(1, productId)
                            names.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else productId }
                        }
                        throw InsufficientStockException(
                            "Stock insuficiente para '$name'. Disponible: ${available / 1000.0}, Requerido: ${required / 1000.0}"
                        )
                    }
                }
            }

            // 6. Payments and Credit Validation
            val totalPaidCents = request.payments.sumOf { it.amountCents }
            if (totalPaidCents < calculatedTotalCents) {
                throw ValidationException(
                    "Monto de pago insuficiente. Total: $${calculatedTotalCents / 100.0}, Pagado: $${totalPaidCents / 100.0}"
                )
            }

            var customerName: String? = null
            var customerRemainingCreditCents: Long? = null
            var supervisorAuditNote: String? = null
            var finalCustomerBalance: Long? = null
            var loyaltyReward: SaleLoyaltyRewardDto? = null

            val creditPayment = request.payments.firstOrNull { it.method == "CREDIT" }
            val loyaltyPayment = request.payments.firstOrNull { it.method == "LOYALTY" }

            if (creditPayment != null || loyaltyPayment != null || !request.customerId.isNullOrBlank()) {
                val custId = request.customerId
                if (custId.isNullOrBlank()) {
                    if (creditPayment != null) {
                        throw ValidationException("La venta a crédito requiere seleccionar un cliente")
                    }
                    if (loyaltyPayment != null) {
                        throw ValidationException("El pago con monedero electrónico requiere seleccionar un cliente")
                    }
                } else {
                    val custStmt = conn.prepareStatement(
                        """
                        SELECT name, credit_enabled, has_credit_limit, credit_limit_cents, current_balance_cents,
                               loyalty_balance_cents, total_loyalty_earned_cents, total_loyalty_redeemed_cents
                        FROM customers WHERE id = ? FOR UPDATE
                        """.trimIndent()
                    )
                    custStmt.setString(1, custId)
                    val custRs = custStmt.executeQuery()
                    if (!custRs.next()) {
                        custRs.close()
                        custStmt.close()
                        throw NotFoundException("Cliente '$custId' no encontrado")
                    }

                    customerName = custRs.getString("name")
                    val creditEnabled = custRs.getBoolean("credit_enabled")
                    val hasCreditLimit = custRs.getBoolean("has_credit_limit")
                    val creditLimit = custRs.getLong("credit_limit_cents")
                    val currentCreditBalance = custRs.getLong("current_balance_cents")
                    var currentLoyaltyBalance = custRs.getLong("loyalty_balance_cents")
                    var currentTotalEarned = custRs.getLong("total_loyalty_earned_cents")
                    var currentTotalRedeemed = custRs.getLong("total_loyalty_redeemed_cents")
                    val previousLoyaltyBalance = currentLoyaltyBalance
                    custRs.close()
                    custStmt.close()

                    // A. Validar y procesar crédito
                    var newCreditBalance = currentCreditBalance
                    if (creditPayment != null) {
                        if (!creditEnabled) {
                            throw ValidationException("El cliente '$customerName' no tiene crédito habilitado")
                        }
                        newCreditBalance = currentCreditBalance + creditPayment.amountCents
                        if (hasCreditLimit && newCreditBalance > creditLimit) {
                            val supPin = request.supervisorAuthPin
                            if (supPin.isNullOrBlank()) {
                                throw CreditLimitExceededException(
                                    "Límite de crédito excedido para '$customerName'. Límite: $${creditLimit / 100.0}, Saldo nuevo sería: $${newCreditBalance / 100.0}. Se requiere autorización de supervisor."
                                )
                            }
                            val supStmt = conn.prepareStatement(
                                "SELECT name, pin_hash FROM users WHERE role IN ('ADMIN', 'SUPERVISOR') AND is_active = TRUE"
                            )
                            val supRs = supStmt.executeQuery()
                            var authorizedSupervisor: String? = null
                            while (supRs.next()) {
                                val hash = supRs.getString("pin_hash")
                                if (Security.verifyPin(supPin, hash)) {
                                    authorizedSupervisor = supRs.getString("name")
                                    break
                                }
                            }
                            supRs.close()
                            supStmt.close()
                            if (authorizedSupervisor == null) {
                                throw ForbiddenException("PIN de supervisor inválido o no autorizado para autorizar sobregiro de crédito")
                            }
                            supervisorAuditNote = "Sobregiro autorizado por $authorizedSupervisor" +
                                if (!request.supervisorAuthReason.isNullOrBlank()) " (${request.supervisorAuthReason})" else ""
                        }
                        customerRemainingCreditCents = if (!hasCreditLimit) 99999900L else (creditLimit - newCreditBalance).coerceAtLeast(0L)
                        finalCustomerBalance = newCreditBalance
                    }

                    // B. Validar y procesar Monedero Electrónico (Redeem)
                    var redeemedCents = 0L
                    if (loyaltyPayment != null) {
                        if (currentLoyaltyBalance < loyaltyPayment.amountCents) {
                            throw ValidationException("Saldo insuficiente en monedero: disponible $${currentLoyaltyBalance / 100.0}, requerido $${loyaltyPayment.amountCents / 100.0}")
                        }
                        if (settings.loyaltyMinRedeemCents > 0L && currentLoyaltyBalance < settings.loyaltyMinRedeemCents) {
                            throw ValidationException("El saldo mínimo para redimir monedero es de $${settings.loyaltyMinRedeemCents / 100.0}")
                        }
                        redeemedCents = loyaltyPayment.amountCents
                        currentLoyaltyBalance -= redeemedCents
                        currentTotalRedeemed += redeemedCents
                    }

                    // C. Calcular Cashback Ganado (solo sobre dinero real: efectivo, tarjeta, transferencia)
                    var earnedCents = 0L
                    if (settings.loyaltyEnabled) {
                        val realMoneyPaid = request.payments
                            .filter { it.method != "LOYALTY" }
                            .sumOf { it.amountCents }
                            .coerceAtMost(calculatedTotalCents)
                        if (realMoneyPaid > 0L && settings.loyaltyCashbackPercent > 0.0) {
                            earnedCents = (realMoneyPaid * settings.loyaltyCashbackPercent / 100.0).roundToLong().coerceAtLeast(0L)
                            currentLoyaltyBalance += earnedCents
                            currentTotalEarned += earnedCents
                        }
                    }

                    if (redeemedCents > 0L || earnedCents > 0L) {
                        loyaltyReward = SaleLoyaltyRewardDto(
                            earnedCents = earnedCents,
                            redeemedCents = redeemedCents,
                            previousBalanceCents = previousLoyaltyBalance,
                            finalBalanceCents = currentLoyaltyBalance,
                            cashbackPercentApplied = settings.loyaltyCashbackPercent
                        )
                    }

                    val nowCust = System.currentTimeMillis()
                    val updateCustStmt = conn.prepareStatement(
                        """
                        UPDATE customers 
                        SET current_balance_cents = ?, loyalty_balance_cents = ?, total_loyalty_earned_cents = ?, total_loyalty_redeemed_cents = ?, updated_at = ?
                        WHERE id = ?
                        """.trimIndent()
                    )
                    updateCustStmt.setLong(1, newCreditBalance)
                    updateCustStmt.setLong(2, currentLoyaltyBalance)
                    updateCustStmt.setLong(3, currentTotalEarned)
                    updateCustStmt.setLong(4, currentTotalRedeemed)
                    updateCustStmt.setLong(5, nowCust)
                    updateCustStmt.setString(6, custId)
                    updateCustStmt.executeUpdate()
                    updateCustStmt.close()
                }
            }

            // 7. Atomic Consecutivo (Folio)
            val counterStmt = conn.prepareStatement("SELECT current_value FROM counters WHERE name = 'sale_folio' FOR UPDATE")
            val counterRs = counterStmt.executeQuery()
            counterRs.next()
            val nextFolio = counterRs.getLong("current_value") + 1L
            counterRs.close()
            counterStmt.close()

            val updateCounterStmt = conn.prepareStatement("UPDATE counters SET current_value = ? WHERE name = 'sale_folio'")
            updateCounterStmt.setLong(1, nextFolio)
            updateCounterStmt.executeUpdate()
            updateCounterStmt.close()

            // 8. Record Sale
            val saleId = "sale-" + UUID.randomUUID().toString().take(8)
            val now = System.currentTimeMillis()

            val totalDiscountCents = validatedLines.sumOf { it.discountCents }.coerceAtLeast(request.discountCents)

            val insertSaleStmt = conn.prepareStatement(
                """
                INSERT INTO sales (id, folio, cash_session_id, user_id, customer_id, status, subtotal_cents, tax_cents, total_cents, discount_cents, idempotency_key, created_at)
                VALUES (?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            insertSaleStmt.setString(1, saleId)
            insertSaleStmt.setLong(2, nextFolio)
            insertSaleStmt.setString(3, request.cashSessionId)
            insertSaleStmt.setString(4, request.cashierUserId)
            insertSaleStmt.setString(5, request.customerId)
            insertSaleStmt.setLong(6, calculatedSubtotalCents)
            insertSaleStmt.setLong(7, calculatedTaxCents)
            insertSaleStmt.setLong(8, calculatedTotalCents)
            insertSaleStmt.setLong(9, totalDiscountCents)
            insertSaleStmt.setString(10, request.idempotencyKey)
            insertSaleStmt.setLong(11, now)
            insertSaleStmt.executeUpdate()
            insertSaleStmt.close()

            // Capture cost after locking stock, so a completed purchase is reflected in this sale.
            val catalogIds = validatedLines.flatMap { line ->
                listOfNotNull(line.productId) + line.kitStock.map { it.productId }
            }.toSet()
            val catalogSnapshot = loadSaleCatalogSnapshots(conn, catalogIds)

            // 9. Record Lines & Deduct Stock
            val insertLineStmt = conn.prepareStatement(
                """
                INSERT INTO sale_lines (
                    id, sale_id, product_id, presentation_id, description, quantity_millis, unit_price_cents, tax_rate,
                    tax_cents, total_cents, discount_cents, discount_percent, kit_stock_json,
                    cost_snapshot_cents, base_factor_millis, snapshot_product_name, snapshot_sku, snapshot_base_unit,
                    snapshot_department_id, snapshot_department_name, price_list_level, manual_price_override
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            val lineResponses = mutableListOf<SaleLineItemResponse>()
            for (vl in validatedLines) {
                val lineId = "sline-" + UUID.randomUUID().toString().take(8)
                insertLineStmt.setString(1, lineId)
                insertLineStmt.setString(2, saleId)
                insertLineStmt.setString(3, vl.productId)
                insertLineStmt.setString(4, vl.presentationId)
                insertLineStmt.setString(5, vl.description)
                insertLineStmt.setLong(6, vl.quantityMillis)
                insertLineStmt.setLong(7, vl.unitPriceCents)
                insertLineStmt.setDouble(8, vl.taxRate)
                insertLineStmt.setLong(9, vl.taxCents)
                insertLineStmt.setLong(10, vl.totalCents)
                insertLineStmt.setLong(11, vl.discountCents)
                insertLineStmt.setDouble(12, vl.discountPercent)
                insertLineStmt.setString(13, vl.kitStock.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) })
                val product = vl.productId?.let(catalogSnapshot::get)
                val baseQuantity = multiplyStock(vl.quantityMillis, vl.factorMillis) / 1000L
                val cost = snapshotCost(vl.productId, baseQuantity, if (vl.kitStock.isEmpty()) emptyList() else vl.stock, catalogSnapshot)
                insertLineStmt.setObject(14, cost, java.sql.Types.BIGINT)
                insertLineStmt.setLong(15, vl.factorMillis)
                insertLineStmt.setString(16, product?.name ?: vl.description)
                insertLineStmt.setString(17, product?.sku)
                insertLineStmt.setString(18, product?.unit ?: "PZA")
                insertLineStmt.setString(19, product?.departmentId)
                insertLineStmt.setString(20, product?.departmentName)
                insertLineStmt.setObject(21, vl.priceListLevel, java.sql.Types.INTEGER)
                insertLineStmt.setBoolean(22, vl.manualPriceOverride)
                insertLineStmt.executeUpdate()

                lineResponses.add(
                    SaleLineItemResponse(
                        id = lineId,
                        productId = vl.productId,
                        presentationId = vl.presentationId,
                        description = vl.description,
                        quantityMillis = vl.quantityMillis,
                        unitPriceCents = vl.unitPriceCents,
                        taxRate = vl.taxRate,
                        taxCents = vl.taxCents,
                        totalCents = vl.totalCents,
                        discountCents = vl.discountCents,
                        discountPercent = vl.discountPercent,
                        isKit = vl.kitStock.isNotEmpty(),
                        priceListLevel = vl.priceListLevel,
                        manualPriceOverride = vl.manualPriceOverride
                    )
                )

            }
            insertLineStmt.close()
            applyStockMovement(conn, saleStock, "SALE", "Venta folio #$nextFolio", saleId, request.cashierUserId, now)

            // 10. Record Payments
            val insertPaymentStmt = conn.prepareStatement(
                """
                INSERT INTO sale_payments (id, sale_id, method, amount_cents, received_cents, change_cents, reference)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            val paymentResponses = mutableListOf<SalePaymentResponse>()
            var changeLeftToAccount = totalPaidCents - calculatedTotalCents

            for (p in request.payments) {
                val payId = "spay-" + UUID.randomUUID().toString().take(8)
                val changeCents = if (p.method == "CASH") {
                    if (p.receivedCents > p.amountCents) p.receivedCents - p.amountCents else changeLeftToAccount
                } else 0L
                insertPaymentStmt.setString(1, payId)
                insertPaymentStmt.setString(2, saleId)
                insertPaymentStmt.setString(3, p.method)
                insertPaymentStmt.setLong(4, p.amountCents)
                insertPaymentStmt.setLong(5, p.receivedCents)
                insertPaymentStmt.setLong(6, changeCents)
                insertPaymentStmt.setString(7, p.reference)
                insertPaymentStmt.executeUpdate()

                paymentResponses.add(
                    SalePaymentResponse(
                        id = payId,
                        method = p.method,
                        amountCents = p.amountCents,
                        receivedCents = p.receivedCents,
                        changeCents = changeCents,
                        reference = p.reference
                    )
                )
            }
            insertPaymentStmt.close()

            // 11. Record Credit Movement if credit used
            if (creditPayment != null && request.customerId != null) {
                val cmovId = "cmov-" + UUID.randomUUID().toString().take(8)
                val cmovStmt = conn.prepareStatement(
                    """
                    INSERT INTO credit_movements (id, customer_id, type, amount_cents, balance_after_cents, sale_id, cash_session_id, description, user_id, created_at)
                    VALUES (?, ?, 'CHARGE', ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                )
                val chargeDesc = if (supervisorAuditNote != null) {
                    "Cargo por venta folio #$nextFolio [$supervisorAuditNote]"
                } else {
                    "Cargo por venta folio #$nextFolio"
                }
                cmovStmt.setString(1, cmovId)
                cmovStmt.setString(2, request.customerId)
                cmovStmt.setLong(3, creditPayment.amountCents)
                cmovStmt.setLong(4, (finalCustomerBalance ?: 0L))
                cmovStmt.setString(5, saleId)
                cmovStmt.setString(6, request.cashSessionId)
                cmovStmt.setString(7, chargeDesc)
                cmovStmt.setString(8, request.cashierUserId)
                cmovStmt.setLong(9, now)
                cmovStmt.executeUpdate()
                cmovStmt.close()
            }

            // 11b. Record Loyalty Movements if redeemed or earned
            if (loyaltyReward != null && request.customerId != null) {
                if (loyaltyReward.redeemedCents > 0L) {
                    val rmovStmt = conn.prepareStatement(
                        """
                        INSERT INTO customer_loyalty_movements (id, customer_id, sale_id, type, amount_cents, balance_after_cents, description, user_id, created_at)
                        VALUES (?, ?, ?, 'REDEEM', ?, ?, ?, ?, ?)
                        """.trimIndent()
                    )
                    rmovStmt.setString(1, "lm-" + UUID.randomUUID().toString().take(12))
                    rmovStmt.setString(2, request.customerId)
                    rmovStmt.setString(3, saleId)
                    rmovStmt.setLong(4, -loyaltyReward.redeemedCents)
                    val balAfterRedeem = loyaltyReward.previousBalanceCents - loyaltyReward.redeemedCents
                    rmovStmt.setLong(5, balAfterRedeem)
                    rmovStmt.setString(6, "Pago con monedero en venta folio #$nextFolio")
                    rmovStmt.setString(7, request.cashierUserId)
                    rmovStmt.setLong(8, now)
                    rmovStmt.executeUpdate()
                    rmovStmt.close()
                }

                if (loyaltyReward.earnedCents > 0L) {
                    val emovStmt = conn.prepareStatement(
                        """
                        INSERT INTO customer_loyalty_movements (id, customer_id, sale_id, type, amount_cents, balance_after_cents, description, user_id, created_at)
                        VALUES (?, ?, ?, 'EARN', ?, ?, ?, ?, ?)
                        """.trimIndent()
                    )
                    emovStmt.setString(1, "lm-" + UUID.randomUUID().toString().take(12))
                    emovStmt.setString(2, request.customerId)
                    emovStmt.setString(3, saleId)
                    emovStmt.setLong(4, loyaltyReward.earnedCents)
                    emovStmt.setLong(5, loyaltyReward.finalBalanceCents)
                    emovStmt.setString(6, "Cashback (${loyaltyReward.cashbackPercentApplied}%) en venta folio #$nextFolio")
                    emovStmt.setString(7, request.cashierUserId)
                    emovStmt.setLong(8, now)
                    emovStmt.executeUpdate()
                    emovStmt.close()
                }
            }

            // Fetch Cashier Name
            val userStmt = conn.prepareStatement("SELECT name FROM users WHERE id = ?")
            userStmt.setString(1, request.cashierUserId)
            val userRs = userStmt.executeQuery()
            val cashierName = if (userRs.next()) userRs.getString("name") else "Cajero"
            userRs.close()
            userStmt.close()

            val response = SaleResponse(
                id = saleId,
                folio = nextFolio,
                cashSessionId = request.cashSessionId,
                cashierName = cashierName,
                customerName = customerName,
                customerRemainingCreditCents = customerRemainingCreditCents,
                status = "COMPLETED",
                subtotalCents = calculatedSubtotalCents,
                taxCents = calculatedTaxCents,
                totalCents = calculatedTotalCents,
                discountCents = totalDiscountCents,
                lines = lineResponses,
                payments = paymentResponses,
                createdAt = now,
                loyaltyReward = loyaltyReward
            )

            conn.prepareStatement("UPDATE idempotency_keys SET response_body = ? WHERE key_id = ?").use { statement ->
                statement.setString(1, json.encodeToString(response))
                statement.setString(2, request.idempotencyKey)
                statement.executeUpdate()
            }

            response
        }
    }

    fun getSaleById(id: String, ownerId: String? = null): SaleResponse = db.inTransaction { conn ->
        requireSaleOwner(conn, id, ownerId)
        requireOpenSaleRead(conn, id)
        getSaleById(conn, id)
    }

    private fun requireSaleOwner(connection: Connection, saleId: String, ownerId: String?) {
        if (ownerId == null) return
        connection.prepareStatement("SELECT 1 FROM sales WHERE id = ? AND user_id = ?").use {
            it.setString(1, saleId)
            it.setString(2, ownerId)
            it.executeQuery().use { row -> if (!row.next()) throw NotFoundException("Venta no encontrada.") }
        }
    }

    fun getClosedShiftSales(sessionId: String, folio: Long?, beforeFolio: Long?, ownerId: String? = null): ClosedShiftSalesPage {
        if ((folio != null && folio <= 0) || (beforeFolio != null && beforeFolio <= 0)) {
            throw ValidationException("Indica un folio positivo.")
        }
        return db.inTransaction { conn ->
            requireClosedShift(conn, sessionId)
            val sql = buildString {
                append(
                    """
                    SELECT s.id, s.folio, COALESCE(u.name, s.user_id), s.status, s.total_cents, s.created_at
                    FROM sales s LEFT JOIN users u ON u.id = s.user_id
                    WHERE s.cash_session_id = ?
                    """.trimIndent()
                )
                if (folio != null) append(" AND s.folio = ?")
                if (beforeFolio != null) append(" AND s.folio < ?")
                if (ownerId != null) append(" AND s.user_id = ?")
                append(" ORDER BY s.folio DESC LIMIT ?")
            }
            val size = 30
            val results = conn.prepareStatement(sql).use { query ->
                var column = 1
                query.setString(column++, sessionId)
                if (folio != null) query.setLong(column++, folio)
                if (beforeFolio != null) query.setLong(column++, beforeFolio)
                if (ownerId != null) query.setString(column++, ownerId)
                query.setInt(column, size + 1)
                query.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(ClosedShiftSaleRow(rows.getString(1), rows.getLong(2), rows.getString(3),
                                rows.getString(4), rows.getLong(5), rows.getLong(6)))
                        }
                    }
                }
            }
            val page = results.take(size)
            ClosedShiftSalesPage(page, if (results.size > size) page.last().folio else null)
        }
    }

    fun getClosedShiftSale(sessionId: String, id: String, ownerId: String? = null): ClosedShiftSaleDetail = db.inTransaction { conn ->
        requireClosedShift(conn, sessionId)
        requireSaleOwner(conn, id, ownerId)
        val cancellation = conn.prepareStatement(
            "SELECT cancellation_reason, cancelled_at FROM sales WHERE id = ? AND cash_session_id = ? FOR UPDATE"
        ).use { query ->
            query.setString(1, id)
            query.setString(2, sessionId)
            query.executeQuery().use { row ->
                if (!row.next()) throw NotFoundException("No se encontró la venta en este turno.")
                val cancelledAt = row.getLong("cancelled_at")
                if (row.wasNull()) null else HistoricalSaleCancellation(row.getString("cancellation_reason").orEmpty(), cancelledAt)
            }
        }
        ClosedShiftSaleDetail(getSaleById(conn, id), cancellation)
    }

    private fun requireClosedShift(conn: Connection, sessionId: String) {
        conn.prepareStatement("SELECT id FROM cash_sessions WHERE id = ? AND status = 'CLOSED'").use {
            it.setString(1, sessionId)
            it.executeQuery().use { row ->
                if (!row.next()) throw NotFoundException("No se encontró el turno cerrado.")
            }
        }
    }

    private fun requireOpenSaleRead(conn: Connection, id: String) {
        conn.prepareStatement(
            "SELECT cs.status FROM sales s JOIN cash_sessions cs ON cs.id = s.cash_session_id WHERE s.id = ?"
        ).use {
            it.setString(1, id)
            it.executeQuery().use { row ->
                if (!row.next()) throw NotFoundException("Venta '$id' no encontrada")
                if (row.getString(1) != "OPEN") throw ForbiddenException("Consulta la venta cerrada desde el historial de cortes con PIN de encargado.")
            }
        }
    }

    private fun getSaleById(conn: Connection, id: String): SaleResponse {
        val stmt = conn.prepareStatement(
            """
            SELECT s.id, s.folio, s.cash_session_id, s.user_id, u.name as cashier_name,
                   s.customer_id, c.name as customer_name, c.credit_limit_cents, c.current_balance_cents,
                   s.status, s.subtotal_cents, s.tax_cents, s.total_cents, s.discount_cents, s.created_at
            FROM sales s
            JOIN users u ON s.user_id = u.id
            LEFT JOIN customers c ON s.customer_id = c.id
            WHERE s.id = ?
            """.trimIndent()
        )
        stmt.setString(1, id)
        val rs = stmt.executeQuery()
        if (!rs.next()) {
            rs.close()
            stmt.close()
            throw NotFoundException("Venta '$id' no encontrada")
        }

        val saleId = rs.getString("id")
        val folio = rs.getLong("folio")
        val sessionId = rs.getString("cash_session_id")
        val cashierName = rs.getString("cashier_name")
        val customerName = rs.getString("customer_name")
        val limit = rs.getLong("credit_limit_cents")
        val balance = rs.getLong("current_balance_cents")
        val remCredit = if (customerName != null) (limit - balance).coerceAtLeast(0L) else null
        val status = rs.getString("status")
        val subtotal = rs.getLong("subtotal_cents")
        val tax = rs.getLong("tax_cents")
        val total = rs.getLong("total_cents")
        val discountCents = rs.getLong("discount_cents")
        val createdAt = rs.getLong("created_at")
        rs.close()
        stmt.close()

        // Fetch Lines
        val lineStmt = conn.prepareStatement(
            """
            SELECT sl.id, sl.product_id, sl.presentation_id, sl.description, sl.quantity_millis,
                   sl.unit_price_cents, sl.tax_rate, sl.tax_cents, sl.total_cents, sl.discount_cents, sl.discount_percent, sl.kit_stock_json, sl.price_list_level, sl.manual_price_override,
                   COALESCE((SELECT SUM(srl.quantity_millis) FROM sale_refund_lines srl WHERE srl.sale_line_id = sl.id), 0) as refunded_quantity_millis
            FROM sale_lines sl
            WHERE sl.sale_id = ?
            """.trimIndent()
        )
        lineStmt.setString(1, saleId)
        val lineRs = lineStmt.executeQuery()
        val lines = mutableListOf<SaleLineItemResponse>()
        while (lineRs.next()) {
            lines.add(
                SaleLineItemResponse(
                    id = lineRs.getString("id"),
                    productId = lineRs.getString("product_id"),
                    presentationId = lineRs.getString("presentation_id"),
                    description = lineRs.getString("description"),
                    quantityMillis = lineRs.getLong("quantity_millis"),
                    unitPriceCents = lineRs.getLong("unit_price_cents"),
                    taxRate = lineRs.getDouble("tax_rate"),
                    taxCents = lineRs.getLong("tax_cents"),
                    totalCents = lineRs.getLong("total_cents"),
                    discountCents = lineRs.getLong("discount_cents"),
                    discountPercent = lineRs.getDouble("discount_percent"),
                    refundedQuantityMillis = lineRs.getLong("refunded_quantity_millis"),
                    isKit = lineRs.getString("kit_stock_json") != null,
                    priceListLevel = lineRs.getObject("price_list_level", Integer::class.java)?.toInt(),
                    manualPriceOverride = lineRs.getBoolean("manual_price_override")
                )
            )
        }
        lineRs.close()
        lineStmt.close()

        // Fetch Payments
        val payStmt = conn.prepareStatement(
            "SELECT id, method, amount_cents, received_cents, change_cents, reference FROM sale_payments WHERE sale_id = ?"
        )
        payStmt.setString(1, saleId)
        val payRs = payStmt.executeQuery()
        val payments = mutableListOf<SalePaymentResponse>()
        while (payRs.next()) {
            payments.add(
                SalePaymentResponse(
                    id = payRs.getString("id"),
                    method = payRs.getString("method"),
                    amountCents = payRs.getLong("amount_cents"),
                    receivedCents = payRs.getLong("received_cents"),
                    changeCents = payRs.getLong("change_cents"),
                    reference = payRs.getString("reference")
                )
            )
        }
        payRs.close()
        payStmt.close()

        val refunds = getRefundsBySaleIdInternal(conn, saleId)

        return SaleResponse(
            id = saleId,
            folio = folio,
            cashSessionId = sessionId,
            cashierName = cashierName,
            customerName = customerName,
            customerRemainingCreditCents = remCredit,
            status = status,
            subtotalCents = subtotal,
            taxCents = tax,
            totalCents = total,
            discountCents = discountCents,
            lines = lines,
            payments = payments,
            createdAt = createdAt,
            refunds = refunds
        )
    }

    fun cancelSale(saleId: String, request: CancelSaleRequest, actorId: String? = null, ownerId: String? = null): SaleResponse {
        return db.inTransaction { conn ->
            requireSaleOwner(conn, saleId, ownerId)
            conn.prepareStatement("SELECT id FROM sales WHERE id = ? FOR UPDATE").use { lock ->
                lock.setString(1, saleId)
                lock.executeQuery().use { if (!it.next()) throw NotFoundException("Venta '$saleId' no encontrada") }
            }
            val sale = getSaleById(conn, saleId)
            conn.prepareStatement("SELECT status FROM cash_sessions WHERE id = ? FOR UPDATE").use {
                it.setString(1, sale.cashSessionId)
                it.executeQuery().use { row ->
                    if (!row.next() || row.getString(1) != "OPEN") {
                        throw SessionClosedException("La venta pertenece a un turno cerrado. Para devolver mercancía, registra una devolución en un turno abierto.")
                    }
                }
            }
            if (sale.status != "COMPLETED") {
                throw ValidationException("Solo se pueden cancelar ventas con estado COMPLETED")
            }

            val refCheckStmt = conn.prepareStatement("SELECT COUNT(*) FROM sale_refunds WHERE sale_id = ?")
            refCheckStmt.setString(1, saleId)
            val refCheckRs = refCheckStmt.executeQuery()
            refCheckRs.next()
            val existingRefunds = refCheckRs.getInt(1)
            refCheckRs.close()
            refCheckStmt.close()
            if (existingRefunds > 0) {
                throw ValidationException("No se puede cancelar una venta que ya tiene devoluciones registradas. Realice una devolución por las partidas restantes.")
            }

            if (actorId != null) writeAudit(conn, "transactions.cancel", actorId, saleId, request.authorizedByUserId)

            val now = System.currentTimeMillis()

            val restoredStock = conn.prepareStatement("""
                SELECT product_id, -SUM(quantity_millis) AS restored
                FROM stock_movements WHERE type = 'SALE' AND reference = ?
                GROUP BY product_id ORDER BY product_id
            """.trimIndent()).use { statement ->
                statement.setString(1, saleId)
                statement.executeQuery().use { rows ->
                    buildMap {
                        while (rows.next()) put(rows.getString("product_id"), rows.getLong("restored"))
                    }
                }
            }
            applyStockMovement(
                conn, restoredStock, "REFUND", "Cancelación venta folio #${sale.folio}: ${request.reason}",
                saleId, request.authorizedByUserId ?: "admin", now
            )

            // 2. Revert credit if applicable
            val creditPayment = sale.payments.firstOrNull { it.method == "CREDIT" }
            if (creditPayment != null) {
                val custSelectStmt = conn.prepareStatement(
                    "SELECT id, current_balance_cents FROM customers WHERE id = (SELECT customer_id FROM sales WHERE id = ?) FOR UPDATE"
                )
                custSelectStmt.setString(1, saleId)
                val custRs = custSelectStmt.executeQuery()
                if (custRs.next()) {
                    val custId = custRs.getString("id")
                    val currentBal = custRs.getLong("current_balance_cents")
                    val newBal = (currentBal - creditPayment.amountCents).coerceAtLeast(0L)
                    val nowCancel = System.currentTimeMillis()

                    val updateCustStmt = conn.prepareStatement(
                        "UPDATE customers SET current_balance_cents = ?, updated_at = ? WHERE id = ?"
                    )
                    updateCustStmt.setLong(1, newBal)
                    updateCustStmt.setLong(2, nowCancel)
                    updateCustStmt.setString(3, custId)
                    updateCustStmt.executeUpdate()
                    updateCustStmt.close()

                    val revMovId = "cmov-" + UUID.randomUUID().toString().take(8)
                    val revStmt = conn.prepareStatement(
                        """
                        INSERT INTO credit_movements (id, customer_id, type, amount_cents, balance_after_cents, sale_id, description, user_id, created_at)
                        VALUES (?, ?, 'REVERSAL', ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                    )
                    revStmt.setString(1, revMovId)
                    revStmt.setString(2, custId)
                    revStmt.setLong(3, creditPayment.amountCents)
                    revStmt.setLong(4, newBal)
                    revStmt.setString(5, saleId)
                    revStmt.setString(6, "Cancelación de venta folio #${sale.folio}: ${request.reason}")
                    revStmt.setString(7, request.authorizedByUserId ?: "admin")
                    revStmt.setLong(8, nowCancel)
                    revStmt.executeUpdate()
                    revStmt.close()
                }
                custRs.close()
                custSelectStmt.close()
            }

            // 3. Revert cash from session if cash was paid
            val cashPaidCents = sale.payments.filter { it.method == "CASH" }.sumOf { it.amountCents }
            if (cashPaidCents > 0L) {
                val updateCashStmt = conn.prepareStatement(
                    "UPDATE cash_sessions SET expected_cash_cents = expected_cash_cents - ? WHERE id = ?"
                )
                updateCashStmt.setLong(1, cashPaidCents)
                updateCashStmt.setString(2, sale.cashSessionId)
                updateCashStmt.executeUpdate()
                updateCashStmt.close()
            }

            // 4. Update Sale Status
            conn.prepareStatement("UPDATE sales SET status = 'CANCELLED', cancellation_reason = ?, cancelled_at = ? WHERE id = ?").use {
                it.setString(1, request.reason.trim())
                it.setLong(2, now)
                it.setString(3, saleId)
                it.executeUpdate()
            }

            sale.copy(status = "CANCELLED")
        }
    }

    fun getSales(
        sessionId: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        folio: Long? = null,
        status: String? = null,
        limit: Int = 100,
        ownerId: String? = null
    ): List<SaleResponse> {
        return db.inTransaction { conn ->
            if (!sessionId.isNullOrBlank()) {
                conn.prepareStatement("SELECT status FROM cash_sessions WHERE id = ?").use {
                    it.setString(1, sessionId)
                    it.executeQuery().use { row ->
                        if (row.next() && row.getString(1) != "OPEN") {
                            throw ForbiddenException("Consulta el turno cerrado desde el historial de cortes con PIN de encargado.")
                        }
                    }
                }
            }
            val sql = StringBuilder(
                """
                SELECT s.id, s.folio, s.cash_session_id, s.user_id, u.name as cashier_name,
                       s.customer_id, c.name as customer_name, c.credit_limit_cents, c.current_balance_cents,
                       s.status, s.subtotal_cents, s.tax_cents, s.total_cents, s.discount_cents, s.created_at
                FROM sales s
                JOIN users u ON s.user_id = u.id
                LEFT JOIN customers c ON s.customer_id = c.id
                JOIN cash_sessions cs ON cs.id = s.cash_session_id
                WHERE cs.status = 'OPEN'
                """.trimIndent()
            )

            val params = mutableListOf<Any>()
            if (ownerId != null) {
                sql.append(" AND s.user_id = ?")
                params.add(ownerId)
            }
            if (!sessionId.isNullOrBlank()) {
                sql.append(" AND s.cash_session_id = ?")
                params.add(sessionId)
            }
            if (folio != null) {
                sql.append(" AND s.folio = ?")
                params.add(folio)
            }
            if (!status.isNullOrBlank()) {
                sql.append(" AND s.status = ?")
                params.add(status.uppercase())
            }
            if (startDate != null) {
                sql.append(" AND s.created_at >= ?")
                params.add(startDate)
            }
            if (endDate != null) {
                sql.append(" AND s.created_at <= ?")
                params.add(endDate)
            }

            sql.append(" ORDER BY s.created_at DESC LIMIT ?")
            params.add(limit.coerceIn(1, 500))

            val stmt = conn.prepareStatement(sql.toString())
            for ((index, param) in params.withIndex()) {
                when (param) {
                    is String -> stmt.setString(index + 1, param)
                    is Long -> stmt.setLong(index + 1, param)
                    is Int -> stmt.setInt(index + 1, param)
                }
            }

            val rs = stmt.executeQuery()
            data class SaleHeader(
                val id: String,
                val folio: Long,
                val sessionId: String,
                val cashierName: String,
                val customerName: String?,
                val remainingCredit: Long?,
                val status: String,
                val subtotal: Long,
                val tax: Long,
                val total: Long,
                val discountCents: Long,
                val createdAt: Long
            )

            val headers = mutableListOf<SaleHeader>()
            while (rs.next()) {
                val customerName = rs.getString("customer_name")
                val limitCredit = rs.getLong("credit_limit_cents")
                val balanceCredit = rs.getLong("current_balance_cents")
                val remCredit = if (customerName != null) (limitCredit - balanceCredit).coerceAtLeast(0L) else null

                headers.add(
                    SaleHeader(
                        id = rs.getString("id"),
                        folio = rs.getLong("folio"),
                        sessionId = rs.getString("cash_session_id"),
                        cashierName = rs.getString("cashier_name"),
                        customerName = customerName,
                        remainingCredit = remCredit,
                        status = rs.getString("status"),
                        subtotal = rs.getLong("subtotal_cents"),
                        tax = rs.getLong("tax_cents"),
                        total = rs.getLong("total_cents"),
                        discountCents = rs.getLong("discount_cents"),
                        createdAt = rs.getLong("created_at")
                    )
                )
            }
            rs.close()
            stmt.close()

            headers.map { h ->
                val lineStmt = conn.prepareStatement(
                    """
                    SELECT sl.id, sl.product_id, sl.presentation_id, sl.description, sl.quantity_millis,
                           sl.unit_price_cents, sl.tax_rate, sl.tax_cents, sl.total_cents, sl.discount_cents, sl.discount_percent, sl.kit_stock_json, sl.price_list_level, sl.manual_price_override,
                           COALESCE((SELECT SUM(srl.quantity_millis) FROM sale_refund_lines srl WHERE srl.sale_line_id = sl.id), 0) as refunded_quantity_millis
                    FROM sale_lines sl
                    WHERE sl.sale_id = ?
                    """.trimIndent()
                )
                lineStmt.setString(1, h.id)
                val lineRs = lineStmt.executeQuery()
                val lines = mutableListOf<SaleLineItemResponse>()
                while (lineRs.next()) {
                    lines.add(
                        SaleLineItemResponse(
                            id = lineRs.getString("id"),
                            productId = lineRs.getString("product_id"),
                            presentationId = lineRs.getString("presentation_id"),
                            description = lineRs.getString("description"),
                            quantityMillis = lineRs.getLong("quantity_millis"),
                            unitPriceCents = lineRs.getLong("unit_price_cents"),
                            taxRate = lineRs.getDouble("tax_rate"),
                            taxCents = lineRs.getLong("tax_cents"),
                            totalCents = lineRs.getLong("total_cents"),
                            discountCents = lineRs.getLong("discount_cents"),
                            discountPercent = lineRs.getDouble("discount_percent"),
                            refundedQuantityMillis = lineRs.getLong("refunded_quantity_millis"),
                            isKit = lineRs.getString("kit_stock_json") != null,
                            priceListLevel = lineRs.getObject("price_list_level", Integer::class.java)?.toInt(),
                            manualPriceOverride = lineRs.getBoolean("manual_price_override")
                        )
                    )
                }
                lineRs.close()
                lineStmt.close()

                val payStmt = conn.prepareStatement(
                    "SELECT id, method, amount_cents, received_cents, change_cents, reference FROM sale_payments WHERE sale_id = ?"
                )
                payStmt.setString(1, h.id)
                val payRs = payStmt.executeQuery()
                val payments = mutableListOf<SalePaymentResponse>()
                while (payRs.next()) {
                    payments.add(
                        SalePaymentResponse(
                            id = payRs.getString("id"),
                            method = payRs.getString("method"),
                            amountCents = payRs.getLong("amount_cents"),
                            receivedCents = payRs.getLong("received_cents"),
                            changeCents = payRs.getLong("change_cents"),
                            reference = payRs.getString("reference")
                        )
                    )
                }
                payRs.close()
                payStmt.close()

                val refunds = getRefundsBySaleIdInternal(conn, h.id)

                SaleResponse(
                    id = h.id,
                    folio = h.folio,
                    cashSessionId = h.sessionId,
                    cashierName = h.cashierName,
                    customerName = h.customerName,
                    customerRemainingCreditCents = h.remainingCredit,
                    status = h.status,
                    subtotalCents = h.subtotal,
                    taxCents = h.tax,
                    totalCents = h.total,
                    discountCents = h.discountCents,
                    lines = lines,
                    payments = payments,
                    createdAt = h.createdAt,
                    refunds = refunds
                )
            }
        }
    }

    private fun getRefundsBySaleIdInternal(conn: Connection, saleId: String): List<SaleRefundResponse> {
        val stmt = conn.prepareStatement(
            """
            SELECT r.id, r.folio, r.sale_id, s.folio as sale_folio, r.cash_session_id,
                   u.name as cashier_name, su.name as supervisor_name,
                   r.reason, r.notes, r.refund_method, r.subtotal_cents, r.tax_cents, r.total_cents, r.created_at
            FROM sale_refunds r
            JOIN sales s ON r.sale_id = s.id
            JOIN users u ON r.user_id = u.id
            LEFT JOIN users su ON r.authorized_by_user_id = su.id
            WHERE r.sale_id = ?
            ORDER BY r.created_at ASC
            """.trimIndent()
        )
        stmt.setString(1, saleId)
        val rs = stmt.executeQuery()

        data class RefHeader(
            val id: String,
            val folio: Long,
            val saleId: String,
            val saleFolio: Long,
            val cashSessionId: String,
            val cashierName: String,
            val supervisorName: String?,
            val reason: String,
            val notes: String,
            val refundMethod: String,
            val subtotalCents: Long,
            val taxCents: Long,
            val totalCents: Long,
            val createdAt: Long
        )

        val headers = mutableListOf<RefHeader>()
        while (rs.next()) {
            headers.add(
                RefHeader(
                    id = rs.getString("id"),
                    folio = rs.getLong("folio"),
                    saleId = rs.getString("sale_id"),
                    saleFolio = rs.getLong("sale_folio"),
                    cashSessionId = rs.getString("cash_session_id"),
                    cashierName = rs.getString("cashier_name"),
                    supervisorName = rs.getString("supervisor_name"),
                    reason = rs.getString("reason"),
                    notes = rs.getString("notes"),
                    refundMethod = rs.getString("refund_method"),
                    subtotalCents = rs.getLong("subtotal_cents"),
                    taxCents = rs.getLong("tax_cents"),
                    totalCents = rs.getLong("total_cents"),
                    createdAt = rs.getLong("created_at")
                )
            )
        }
        rs.close()
        stmt.close()

        return headers.map { h ->
            val lStmt = conn.prepareStatement(
                """
                SELECT id, sale_line_id, product_id, presentation_id, description,
                       quantity_millis, unit_price_cents, tax_rate, tax_cents, total_cents, reenter_stock
                FROM sale_refund_lines
                WHERE refund_id = ?
                """.trimIndent()
            )
            lStmt.setString(1, h.id)
            val lRs = lStmt.executeQuery()
            val lines = mutableListOf<SaleRefundLineResponse>()
            while (lRs.next()) {
                lines.add(
                    SaleRefundLineResponse(
                        id = lRs.getString("id"),
                        saleLineId = lRs.getString("sale_line_id"),
                        productId = lRs.getString("product_id"),
                        presentationId = lRs.getString("presentation_id"),
                        description = lRs.getString("description"),
                        quantityMillis = lRs.getLong("quantity_millis"),
                        unitPriceCents = lRs.getLong("unit_price_cents"),
                        taxRate = lRs.getDouble("tax_rate"),
                        taxCents = lRs.getLong("tax_cents"),
                        totalCents = lRs.getLong("total_cents"),
                        reenterStock = lRs.getBoolean("reenter_stock")
                    )
                )
            }
            lRs.close()
            lStmt.close()

            SaleRefundResponse(
                id = h.id,
                folio = h.folio,
                saleId = h.saleId,
                saleFolio = h.saleFolio,
                cashSessionId = h.cashSessionId,
                cashierName = h.cashierName,
                authorizedByName = h.supervisorName,
                reason = h.reason,
                notes = h.notes,
                refundMethod = h.refundMethod,
                subtotalCents = h.subtotalCents,
                taxCents = h.taxCents,
                totalCents = h.totalCents,
                lines = lines,
                createdAt = h.createdAt
            )
        }
    }

    fun getRefundsBySaleId(saleId: String, ownerId: String? = null): List<SaleRefundResponse> {
        return db.inTransaction { conn ->
            requireSaleOwner(conn, saleId, ownerId)
            requireOpenSaleRead(conn, saleId)
            getRefundsBySaleIdInternal(conn, saleId)
        }
    }

    fun refundSale(saleId: String, request: CreateSaleRefundRequest, verifyOperator: Boolean = false, ownerId: String? = null): SaleRefundResponse {
        return db.inTransaction { conn ->
            requireSaleOwner(conn, saleId, ownerId)
            // 1. Fetch Sale and lock
            val saleStmt = conn.prepareStatement(
                "SELECT id, folio, cash_session_id, user_id, customer_id, status, subtotal_cents, tax_cents, total_cents FROM sales WHERE id = ? FOR UPDATE"
            )
            saleStmt.setString(1, saleId)
            val saleRs = saleStmt.executeQuery()
            if (!saleRs.next()) {
                saleRs.close()
                saleStmt.close()
                throw NotFoundException("Venta '$saleId' no encontrada")
            }
            val saleFolio = saleRs.getLong("folio")
            val originalSessionId = saleRs.getString("cash_session_id")
            val saleCustomerId = saleRs.getString("customer_id")
            val currentSaleStatus = saleRs.getString("status")
            saleRs.close()
            saleStmt.close()

            if (currentSaleStatus == "CANCELLED") {
                throw ValidationException("No se pueden realizar devoluciones sobre una venta cancelada")
            }
            if (currentSaleStatus == "REFUNDED") {
                throw ValidationException("Esta venta ya ha sido devuelta en su totalidad")
            }
            if (request.lines.isEmpty()) {
                throw ValidationException("Debe especificar al menos una partida a devolver")
            }

            // 2. Validate Active Cash Session
            val sessStmt = conn.prepareStatement("SELECT status FROM cash_sessions WHERE id = ? FOR UPDATE")
            sessStmt.setString(1, request.cashSessionId)
            val sessRs = sessStmt.executeQuery()
            if (!sessRs.next()) {
                sessRs.close()
                sessStmt.close()
                throw ValidationException("La sesión de caja '${request.cashSessionId}' no existe")
            }
            val sessStatus = sessRs.getString("status")
            sessRs.close()
            sessStmt.close()
            if (sessStatus != "OPEN") {
                throw SessionClosedException("La sesión de caja '${request.cashSessionId}' no está abierta para procesar devoluciones")
            }

            if (verifyOperator) requireOperator(conn, request.cashSessionId, request.cashierUserId)
            val settings = settingsService.getSettings()

            // 3. Supervisor PIN check if cashier is CASHIER or supervisorPin provided
            val userStmt = conn.prepareStatement("SELECT role FROM users WHERE id = ?")
            userStmt.setString(1, request.cashierUserId)
            val userRs = userStmt.executeQuery()
            val cashierRole = if (userRs.next()) userRs.getString("role") else "CASHIER"
            userRs.close()
            userStmt.close()

            var authorizedByUserId: String? = null
            val allowedWithoutPin = "transactions.refund" in permissionsForRole(conn, cashierRole)
            if (originalSessionId != request.cashSessionId || !allowedWithoutPin || !request.supervisorPin.isNullOrBlank()) {
                val pin = request.supervisorPin
                if (pin.isNullOrBlank()) {
                    throw ValidationException("Las devoluciones de mercancía requieren autorización de supervisor.")
                }
                val supStmt = conn.prepareStatement(
                    "SELECT id, pin_hash, role FROM users WHERE is_active = TRUE"
                )
                val supRs = supStmt.executeQuery()
                while (supRs.next()) {
                    val supId = supRs.getString("id")
                    val hash = supRs.getString("pin_hash")
                    if ("transactions.refund" in permissionsForRole(conn, supRs.getString("role")) && Security.verifyPin(pin, hash)) {
                        authorizedByUserId = supId
                        break
                    }
                }
                supRs.close()
                supStmt.close()

                if (authorizedByUserId == null) {
                    throw ValidationException("PIN de supervisor incorrecto o no autorizado")
                }
            }

            // 4. Validate Lines and compute refund totals
            data class ValidatedRefundLine(
                val saleLineId: String,
                val productId: String?,
                val presentationId: String?,
                val description: String,
                val quantityMillis: Long,
                val unitPriceCents: Long,
                val taxRate: Double,
                val taxCents: Long,
                val totalCents: Long,
                val reenterStock: Boolean,
                val stock: List<KitComponent>
            )

            val validatedLines = mutableListOf<ValidatedRefundLine>()
            var refundSubtotalCents = 0L
            var refundTaxCents = 0L
            var refundTotalCents = 0L

            if (request.lines.distinctBy { it.saleLineId }.size != request.lines.size) {
                throw ValidationException("Cada partida puede aparecer una sola vez en la devolución.")
            }
            for (reqLine in request.lines) {
                if (reqLine.quantityMillis <= 0L) {
                    throw ValidationException("La cantidad a devolver debe ser mayor a 0")
                }

                // Fetch original line
                val lineStmt = conn.prepareStatement(
                    """
                    SELECT sl.id, sl.product_id, sl.presentation_id, sl.description, sl.quantity_millis,
                           sl.unit_price_cents, sl.tax_rate, sl.tax_cents, sl.total_cents, sl.kit_stock_json,
                           sl.base_factor_millis, p.uses_inventory
                    FROM sale_lines sl
                    LEFT JOIN products p ON sl.product_id = p.id
                    WHERE sl.id = ? AND sl.sale_id = ?
                    """.trimIndent()
                )
                lineStmt.setString(1, reqLine.saleLineId)
                lineStmt.setString(2, saleId)
                val lineRs = lineStmt.executeQuery()
                if (!lineRs.next()) {
                    lineRs.close()
                    lineStmt.close()
                    throw ValidationException("La partida '${reqLine.saleLineId}' no pertenece a la venta '$saleId'")
                }

                val origQtyMillis = lineRs.getLong("quantity_millis")
                val origTotalCents = lineRs.getLong("total_cents")
                val origUnitPriceCents = lineRs.getLong("unit_price_cents")
                val origTaxCents = lineRs.getLong("tax_cents")
                val taxRate = lineRs.getDouble("tax_rate")
                val prodId = lineRs.getString("product_id")
                val presId = lineRs.getString("presentation_id")
                val desc = lineRs.getString("description")
                val usesInventory = lineRs.getBoolean("uses_inventory")
                val originalFactor = lineRs.getLong("base_factor_millis").let { if (lineRs.wasNull()) null else it }
                val kitStock = lineRs.getString("kit_stock_json")?.let { json.decodeFromString<List<KitComponent>>(it) }
                lineRs.close()
                lineStmt.close()

                // Fetch already refunded quantity for this line
                val refCountStmt = conn.prepareStatement(
                    "SELECT COALESCE(SUM(quantity_millis), 0) as already_refunded FROM sale_refund_lines WHERE sale_line_id = ?"
                )
                refCountStmt.setString(1, reqLine.saleLineId)
                val refCountRs = refCountStmt.executeQuery()
                refCountRs.next()
                val alreadyRefunded = refCountRs.getLong("already_refunded")
                refCountRs.close()
                refCountStmt.close()

                val returnable = origQtyMillis - alreadyRefunded
                if (reqLine.quantityMillis > returnable) {
                    throw ValidationException(
                        "No se puede devolver ${reqLine.quantityMillis / 1000.0} de '$desc'. Máximo disponible a devolver: ${returnable / 1000.0}"
                    )
                }

                if (kitStock != null && reqLine.quantityMillis % 1000 != 0L) {
                    throw ValidationException("Los kits se devuelven en cantidades enteras.")
                }
                val lineRefundCents = if (kitStock != null) {
                    proratedKitRefundCents(origTotalCents, origQtyMillis, alreadyRefunded, reqLine.quantityMillis)
                } else ((origTotalCents.toDouble() * reqLine.quantityMillis) / origQtyMillis).roundToLong()
                val stock = when {
                    !reqLine.reenterStock -> emptyList()
                    kitStock != null -> kitStock.map {
                        it.copy(quantityMillis = multiplyStock(it.quantityMillis, reqLine.quantityMillis / 1000L))
                    }
                    usesInventory && prodId != null -> {
                        val factor = originalFactor ?: presId?.let { presentationFactor(conn, it) } ?: 1000L
                        listOf(KitComponent(prodId, multiplyStock(reqLine.quantityMillis, factor) / 1000L))
                    }
                    else -> emptyList()
                }
                val lineTaxCents = when {
                    kitStock != null ->
                        proratedKitRefundCents(origTaxCents, origQtyMillis, alreadyRefunded, reqLine.quantityMillis)
                    taxRate > 0.0 -> ((lineRefundCents.toDouble() * taxRate) / (1.0 + taxRate)).roundToLong()
                    else -> 0L
                }
                val lineSubtotalCents = lineRefundCents - lineTaxCents

                refundSubtotalCents += lineSubtotalCents
                refundTaxCents += lineTaxCents
                refundTotalCents += lineRefundCents

                validatedLines.add(
                    ValidatedRefundLine(
                        saleLineId = reqLine.saleLineId,
                        productId = prodId,
                        presentationId = presId,
                        description = desc,
                        quantityMillis = reqLine.quantityMillis,
                        unitPriceCents = origUnitPriceCents,
                        taxRate = taxRate,
                        taxCents = lineTaxCents,
                        totalCents = lineRefundCents,
                        reenterStock = reqLine.reenterStock,
                        stock = stock
                    )
                )
            }

            // 5. Get Next Refund Folio
            val counterStmt = conn.prepareStatement("SELECT current_value FROM counters WHERE name = 'sale_refund_folio' FOR UPDATE")
            val counterRs = counterStmt.executeQuery()
            counterRs.next()
            val nextRefundFolio = counterRs.getLong("current_value") + 1L
            counterRs.close()
            counterStmt.close()

            val updateCounterStmt = conn.prepareStatement("UPDATE counters SET current_value = ? WHERE name = 'sale_refund_folio'")
            updateCounterStmt.setLong(1, nextRefundFolio)
            updateCounterStmt.executeUpdate()
            updateCounterStmt.close()

            val refundId = "ref-" + UUID.randomUUID().toString().take(8)
            val now = System.currentTimeMillis()

            // 6. Insert sale_refunds
            val insertRefundStmt = conn.prepareStatement(
                """
                INSERT INTO sale_refunds (id, folio, sale_id, cash_session_id, user_id, authorized_by_user_id, refund_method, reason, notes, subtotal_cents, tax_cents, total_cents, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            insertRefundStmt.setString(1, refundId)
            insertRefundStmt.setLong(2, nextRefundFolio)
            insertRefundStmt.setString(3, saleId)
            insertRefundStmt.setString(4, request.cashSessionId)
            insertRefundStmt.setString(5, request.cashierUserId)
            insertRefundStmt.setString(6, authorizedByUserId)
            insertRefundStmt.setString(7, request.refundMethod.uppercase())
            insertRefundStmt.setString(8, request.reason.uppercase())
            insertRefundStmt.setString(9, request.notes)
            insertRefundStmt.setLong(10, refundSubtotalCents)
            insertRefundStmt.setLong(11, refundTaxCents)
            insertRefundStmt.setLong(12, refundTotalCents)
            insertRefundStmt.setLong(13, now)
            insertRefundStmt.executeUpdate()
            insertRefundStmt.close()

            // 7. Insert sale_refund_lines and adjust stock if reenter_stock
            val insertLineStmt = conn.prepareStatement(
                """
                INSERT INTO sale_refund_lines (id, refund_id, sale_line_id, product_id, presentation_id, description, quantity_millis, unit_price_cents, tax_rate, tax_cents, total_cents, reenter_stock, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )

            val refundLinesResponse = mutableListOf<SaleRefundLineResponse>()

            for (vl in validatedLines) {
                val rLineId = "refl-" + UUID.randomUUID().toString().take(8)
                insertLineStmt.setString(1, rLineId)
                insertLineStmt.setString(2, refundId)
                insertLineStmt.setString(3, vl.saleLineId)
                insertLineStmt.setString(4, vl.productId)
                insertLineStmt.setString(5, vl.presentationId)
                insertLineStmt.setString(6, vl.description)
                insertLineStmt.setLong(7, vl.quantityMillis)
                insertLineStmt.setLong(8, vl.unitPriceCents)
                insertLineStmt.setDouble(9, vl.taxRate)
                insertLineStmt.setLong(10, vl.taxCents)
                insertLineStmt.setLong(11, vl.totalCents)
                insertLineStmt.setBoolean(12, vl.reenterStock)
                insertLineStmt.setLong(13, now)
                insertLineStmt.executeUpdate()

                refundLinesResponse.add(
                    SaleRefundLineResponse(
                        id = rLineId,
                        saleLineId = vl.saleLineId,
                        productId = vl.productId,
                        presentationId = vl.presentationId,
                        description = vl.description,
                        quantityMillis = vl.quantityMillis,
                        unitPriceCents = vl.unitPriceCents,
                        taxRate = vl.taxRate,
                        taxCents = vl.taxCents,
                        totalCents = vl.totalCents,
                        reenterStock = vl.reenterStock
                    )
                )
            }
            insertLineStmt.close()

            applyStockMovement(
                conn, summarizeStock(validatedLines.flatMap { it.stock }), "REFUND",
                "Devolución ticket folio #$saleFolio (Nota #$nextRefundFolio): ${request.reason}",
                refundId, request.cashierUserId, now
            )

            // 8. Financial adjustments: Cash Session vs Customer Credit
            if (request.refundMethod.uppercase() == "CREDIT") {
                if (saleCustomerId.isNullOrBlank()) {
                    throw ValidationException("No se puede reembolsar a crédito porque la venta no tiene cliente asociado")
                }
                val custStmt = conn.prepareStatement("SELECT current_balance_cents FROM customers WHERE id = ? FOR UPDATE")
                custStmt.setString(1, saleCustomerId)
                val custRs = custStmt.executeQuery()
                if (custRs.next()) {
                    val currentBal = custRs.getLong("current_balance_cents")
                    val newBal = (currentBal - refundTotalCents).coerceAtLeast(0L)

                    val updateCustStmt = conn.prepareStatement("UPDATE customers SET current_balance_cents = ?, updated_at = ? WHERE id = ?")
                    updateCustStmt.setLong(1, newBal)
                    updateCustStmt.setLong(2, now)
                    updateCustStmt.setString(3, saleCustomerId)
                    updateCustStmt.executeUpdate()
                    updateCustStmt.close()

                    val cmovStmt = conn.prepareStatement(
                        """
                        INSERT INTO credit_movements (id, customer_id, type, amount_cents, balance_after_cents, sale_id, cash_session_id, description, user_id, created_at)
                        VALUES (?, ?, 'REFUND', ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                    )
                    cmovStmt.setString(1, "cmov-" + UUID.randomUUID().toString().take(8))
                    cmovStmt.setString(2, saleCustomerId)
                    cmovStmt.setLong(3, refundTotalCents)
                    cmovStmt.setLong(4, newBal)
                    cmovStmt.setString(5, saleId)
                    cmovStmt.setString(6, request.cashSessionId)
                    cmovStmt.setString(7, "Reembolso ticket folio #$saleFolio (Nota #$nextRefundFolio)")
                    cmovStmt.setString(8, request.cashierUserId)
                    cmovStmt.setLong(9, now)
                    cmovStmt.executeUpdate()
                    cmovStmt.close()
                }
                custRs.close()
                custStmt.close()
            } else if (request.refundMethod.uppercase() == "LOYALTY") {
                if (saleCustomerId.isNullOrBlank()) {
                    throw ValidationException("No se puede reembolsar a monedero porque la venta no tiene cliente asociado")
                }
                val custStmt = conn.prepareStatement("SELECT loyalty_balance_cents FROM customers WHERE id = ? FOR UPDATE")
                custStmt.setString(1, saleCustomerId)
                val custRs = custStmt.executeQuery()
                if (custRs.next()) {
                    val currentLoy = custRs.getLong("loyalty_balance_cents")
                    val newLoy = currentLoy + refundTotalCents

                    val updateCustStmt = conn.prepareStatement("UPDATE customers SET loyalty_balance_cents = ?, updated_at = ? WHERE id = ?")
                    updateCustStmt.setLong(1, newLoy)
                    updateCustStmt.setLong(2, now)
                    updateCustStmt.setString(3, saleCustomerId)
                    updateCustStmt.executeUpdate()
                    updateCustStmt.close()

                    val lmovStmt = conn.prepareStatement(
                        """
                        INSERT INTO customer_loyalty_movements (id, customer_id, sale_id, type, amount_cents, balance_after_cents, description, user_id, created_at)
                        VALUES (?, ?, ?, 'REFUND_CREDIT', ?, ?, ?, ?, ?)
                        """.trimIndent()
                    )
                    lmovStmt.setString(1, "lm-" + UUID.randomUUID().toString().take(12))
                    lmovStmt.setString(2, saleCustomerId)
                    lmovStmt.setString(3, saleId)
                    lmovStmt.setLong(4, refundTotalCents)
                    lmovStmt.setLong(5, newLoy)
                    lmovStmt.setString(6, "Reembolso abonado a monedero electrónico por nota #$nextRefundFolio")
                    lmovStmt.setString(7, request.cashierUserId)
                    lmovStmt.setLong(8, now)
                    lmovStmt.executeUpdate()
                    lmovStmt.close()
                }
                custRs.close()
                custStmt.close()
            } else {
                // Deduct cash from active cash session
                val updateCashStmt = conn.prepareStatement(
                    "UPDATE cash_sessions SET expected_cash_cents = expected_cash_cents - ? WHERE id = ?"
                )
                updateCashStmt.setLong(1, refundTotalCents)
                updateCashStmt.setString(2, request.cashSessionId)
                updateCashStmt.executeUpdate()
                updateCashStmt.close()
            }

            // 8b. Revert proportional cashback if applicable
            if (!saleCustomerId.isNullOrBlank() && settings.loyaltyEnabled && settings.loyaltyCashbackPercent > 0.0) {
                val cashbackToReverse = (refundTotalCents * settings.loyaltyCashbackPercent / 100.0).roundToLong()
                if (cashbackToReverse > 0L) {
                    val updateCustStmt = conn.prepareStatement(
                        "UPDATE customers SET loyalty_balance_cents = GREATEST(0, loyalty_balance_cents - ?), updated_at = ? WHERE id = ?"
                    )
                    updateCustStmt.setLong(1, cashbackToReverse)
                    updateCustStmt.setLong(2, now)
                    updateCustStmt.setString(3, saleCustomerId)
                    updateCustStmt.executeUpdate()
                    updateCustStmt.close()

                    val lstmt = conn.prepareStatement("SELECT loyalty_balance_cents FROM customers WHERE id = ?")
                    lstmt.setString(1, saleCustomerId)
                    val lrs = lstmt.executeQuery()
                    val newLoyBal = if (lrs.next()) lrs.getLong("loyalty_balance_cents") else 0L
                    lrs.close()
                    lstmt.close()

                    val lmovStmt = conn.prepareStatement(
                        """
                        INSERT INTO customer_loyalty_movements (id, customer_id, sale_id, type, amount_cents, balance_after_cents, description, user_id, created_at)
                        VALUES (?, ?, ?, 'REFUND_REVERSAL', ?, ?, ?, ?, ?)
                        """.trimIndent()
                    )
                    lmovStmt.setString(1, "lm-" + UUID.randomUUID().toString().take(12))
                    lmovStmt.setString(2, saleCustomerId)
                    lmovStmt.setString(3, saleId)
                    lmovStmt.setLong(4, -cashbackToReverse)
                    lmovStmt.setLong(5, newLoyBal)
                    lmovStmt.setString(6, "Reversión de cashback (${settings.loyaltyCashbackPercent}%) por devolución nota #$nextRefundFolio")
                    lmovStmt.setString(7, request.cashierUserId)
                    lmovStmt.setLong(8, now)
                    lmovStmt.executeUpdate()
                    lmovStmt.close()
                }
            }

            // 9. Update Sale Status (PARTIALLY_REFUNDED vs REFUNDED)
            val allLinesStmt = conn.prepareStatement(
                """
                SELECT sl.id, sl.quantity_millis,
                       COALESCE((SELECT SUM(srl.quantity_millis) FROM sale_refund_lines srl WHERE srl.sale_line_id = sl.id), 0) as total_refunded
                FROM sale_lines sl
                WHERE sl.sale_id = ?
                """.trimIndent()
            )
            allLinesStmt.setString(1, saleId)
            val allLinesRs = allLinesStmt.executeQuery()
            var allFullyRefunded = true
            while (allLinesRs.next()) {
                val q = allLinesRs.getLong("quantity_millis")
                val ref = allLinesRs.getLong("total_refunded")
                if (ref < q) {
                    allFullyRefunded = false
                }
            }
            allLinesRs.close()
            allLinesStmt.close()

            val newSaleStatus = if (allFullyRefunded) "REFUNDED" else "PARTIALLY_REFUNDED"
            val updateSaleStmt = conn.prepareStatement("UPDATE sales SET status = ? WHERE id = ?")
            updateSaleStmt.setString(1, newSaleStatus)
            updateSaleStmt.setString(2, saleId)
            updateSaleStmt.executeUpdate()
            updateSaleStmt.close()

            // 10. Fetch cashier & supervisor names for response
            val namesStmt = conn.prepareStatement(
                "SELECT id, name FROM users WHERE id IN (?, ?)"
            )
            namesStmt.setString(1, request.cashierUserId)
            namesStmt.setString(2, authorizedByUserId ?: "")
            val namesRs = namesStmt.executeQuery()
            var cashierName = "Cajero"
            var supervisorName: String? = null
            while (namesRs.next()) {
                val uid = namesRs.getString("id")
                val nm = namesRs.getString("name")
                if (uid == request.cashierUserId) cashierName = nm
                if (uid == authorizedByUserId) supervisorName = nm
            }
            namesRs.close()
            namesStmt.close()

            SaleRefundResponse(
                id = refundId,
                folio = nextRefundFolio,
                saleId = saleId,
                saleFolio = saleFolio,
                cashSessionId = request.cashSessionId,
                cashierName = cashierName,
                authorizedByName = supervisorName,
                reason = request.reason.uppercase(),
                notes = request.notes,
                refundMethod = request.refundMethod.uppercase(),
                subtotalCents = refundSubtotalCents,
                taxCents = refundTaxCents,
                totalCents = refundTotalCents,
                lines = refundLinesResponse,
                createdAt = now
            )
        }
    }
    private fun loadKitStock(conn: Connection, productId: String): List<KitComponent> =
        conn.prepareStatement("""
            SELECT k.component_product_id, k.quantity_millis, p.name, p.base_unit, p.is_active, p.uses_inventory
            FROM product_kit_components k JOIN products p ON p.id = k.component_product_id
            WHERE k.kit_product_id = ? ORDER BY k.component_product_id
        """.trimIndent()).use { statement ->
            statement.setString(1, productId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val quantity = rows.getLong("quantity_millis")
                        if (!rows.getBoolean("is_active") || !rows.getBoolean("uses_inventory") ||
                            (rows.getString("base_unit") == "PZA" && quantity % 1000 != 0L)
                        ) throw ValidationException("El componente '${rows.getString("name")}' requiere revisión en el catálogo.")
                        add(KitComponent(rows.getString("component_product_id"), quantity))
                    }
                }
            }
        }

    private fun presentationFactor(conn: Connection, presentationId: String): Long? =
        conn.prepareStatement("SELECT factor_millis FROM product_presentations WHERE id = ?").use { statement ->
            statement.setString(1, presentationId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun multiplyStock(quantity: Long, multiplier: Long): Long =
        try {
            Math.multiplyExact(quantity, multiplier)
        } catch (_: ArithmeticException) {
            throw ValidationException("La cantidad de inventario excede el rango permitido.")
        }

    private fun summarizeStock(components: List<KitComponent>): Map<String, Long> = try {
        components.groupingBy { it.productId }
            .fold(0L) { subtotal, item -> Math.addExact(subtotal, item.quantityMillis) }
            .toSortedMap()
    } catch (_: ArithmeticException) {
        throw ValidationException("La cantidad de inventario excede el rango permitido.")
    }

    private fun applyStockMovement(
        conn: Connection,
        stock: Map<String, Long>,
        type: String,
        reason: String,
        reference: String,
        userId: String,
        timestamp: Long
    ) {
        if (stock.isEmpty()) return
        conn.prepareStatement(
            "UPDATE stock_balances SET quantity_millis = quantity_millis + ?, updated_at = ? WHERE product_id = ?"
        ).use { balance ->
            conn.prepareStatement("""
                INSERT INTO stock_movements (id, product_id, type, quantity_millis, reason, reference, user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { movement ->
                for ((productId, quantity) in stock) {
                    if (quantity == 0L) continue
                    val delta = if (type == "SALE") -quantity else quantity
                    balance.setLong(1, delta)
                    balance.setLong(2, timestamp)
                    balance.setString(3, productId)
                    if (balance.executeUpdate() != 1) throw ValidationException("El producto $productId no tiene saldo de inventario.")
                    movement.setString(1, "mov-${UUID.randomUUID()}")
                    movement.setString(2, productId)
                    movement.setString(3, type)
                    movement.setLong(4, delta)
                    movement.setString(5, reason.take(256))
                    movement.setString(6, reference)
                    movement.setString(7, userId)
                    movement.setLong(8, timestamp)
                    movement.executeUpdate()
                }
            }
        }
    }

}
