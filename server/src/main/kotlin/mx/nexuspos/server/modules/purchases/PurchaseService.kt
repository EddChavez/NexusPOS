package mx.nexuspos.server.modules.purchases

import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.cash.CashMovementRequest
import mx.nexuspos.contracts.purchases.*
import mx.nexuspos.server.modules.cash.CashSessionService
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.ConflictException
import mx.nexuspos.server.platform.IdempotencyMismatchException
import mx.nexuspos.server.platform.NotFoundException
import mx.nexuspos.server.platform.StockChangedException
import mx.nexuspos.server.platform.ValidationException
import java.math.BigInteger
import java.security.MessageDigest
import java.sql.Connection
import java.time.LocalDate
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

class PurchaseService(private val db: DatabaseFactory, private val cashService: CashSessionService = CashSessionService(db)) {
    private val json = Json { ignoreUnknownKeys = true }

    fun listSuppliers(): List<SupplierDto> = db.inTransaction { conn ->
        conn.prepareStatement("SELECT * FROM suppliers ORDER BY is_active DESC, name, id").use {
            it.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.supplier()) } }
        }
    }

    fun saveSupplier(id: String?, request: SaveSupplierRequest): SupplierDto {
        val supplier = SupplierDto(
            id ?: "sup-" + UUID.randomUUID(),
            requiredText(request.name, "nombre del proveedor", 256),
            optionalText(request.taxId, "RFC", 32).uppercase(Locale.ROOT),
            optionalText(request.phone, "teléfono", 64),
            optionalText(request.email, "correo", 254),
            request.isActive
        )
        if (supplier.taxId.length > 32) throw ValidationException("El RFC supera el máximo de 32 caracteres.")
        return db.inTransaction { conn ->
            val sql = if (id == null) {
                "INSERT INTO suppliers (name, tax_id, phone, email, is_active, id) VALUES (?, ?, ?, ?, ?, ?)"
            } else {
                "UPDATE suppliers SET name = ?, tax_id = ?, phone = ?, email = ?, is_active = ? WHERE id = ?"
            }
            conn.prepareStatement(sql).use {
                it.setString(1, supplier.name)
                it.setString(2, supplier.taxId)
                it.setString(3, supplier.phone)
                it.setString(4, supplier.email)
                it.setBoolean(5, supplier.isActive)
                it.setString(6, supplier.id)
                if (it.executeUpdate() != 1) throw NotFoundException("No se encontró el proveedor.")
            }
            supplier
        }
    }

    fun listReceipts(limit: Int, offset: Int): List<PurchaseSummary> = db.inTransaction { conn ->
        conn.prepareStatement(
            """
            SELECT p.*, COALESCE((SELECT SUM(r.total_cents) FROM purchase_returns r WHERE r.receipt_id = p.id), 0) AS returned_cents
            FROM purchase_receipts p ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?
            """.trimIndent()
        ).use {
            it.setInt(1, limit.coerceIn(1, 100))
            it.setInt(2, offset.coerceAtLeast(0))
            it.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.summary()) } }
        }
    }

    fun getReceipt(id: String): PurchaseDetails = db.inTransaction { conn ->
        lockReceipt(conn, id)
        readReceipt(conn, id)
    }

    fun listPayables(includeSettled: Boolean, limit: Int, offset: Int): List<SupplierPayable> =
        db.inTransaction { listSupplierPayables(it, includeSettled, limit, offset) }

    fun getPayable(id: String): SupplierPayableDetail? = db.inTransaction { conn ->
        lockReceipt(conn, id)
        readSupplierPayableDetail(conn, id)
    }

    fun registerPayable(id: String, request: RegisterSupplierPayableRequest, operatorId: String): SupplierPayableDetail {
        val date = request.dueDate.trim()
        if (date.length != 10 || runCatching { LocalDate.parse(date) }.isFailure) {
            throw ValidationException("Captura el vencimiento en formato AAAA-MM-DD.")
        }
        return once<SupplierPayableDetail>("register-payable", request.idempotencyKey, "$id\n$date", operatorId) { conn, now ->
            lockReceipt(conn, id)
            val purchase = readReceipt(conn, id).summary
            if (purchase.documentType != PurchaseDocumentType.INVOICE) {
                throw ValidationException("Solo puedes registrar una cuenta por pagar a partir de una factura de compra.")
            }
            if (readSupplierPayable(conn, id) != null) throw ConflictException("La factura ya tiene una cuenta por pagar.")
            if (purchase.totalCents <= purchase.returnedCents) throw ValidationException("La factura no tiene importe pendiente.")
            conn.prepareStatement("INSERT INTO supplier_payables (receipt_id, due_date, created_by, created_at) VALUES (?, ?, ?, ?)").use {
                it.setString(1, id)
                it.setString(2, date)
                it.setString(3, operatorId)
                it.setLong(4, now)
                it.executeUpdate()
            }
            checkNotNull(readSupplierPayableDetail(conn, id))
        }
    }

    fun recordSupplierPayment(id: String, request: RecordSupplierPaymentRequest, operatorId: String, actorId: String): SupplierPayableDetail {
        val clean = request.copy(reference = optionalText(request.reference, "referencia del pago", 120))
        if (clean.amountCents <= 0) throw ValidationException("Captura un importe mayor a cero.")
        val cash = clean.method == SupplierPaymentMethod.CASH
        val sessionId = clean.cashSessionId
        if (cash && sessionId.isNullOrBlank()) throw ValidationException("Selecciona un turno abierto para registrar el pago en efectivo.")
        if (!cash && sessionId != null) throw ValidationException("Solo un pago en efectivo genera una salida de caja.")
        val payload = "$id\n" + json.encodeToString(clean) + if (cash) "\n$actorId" else ""
        return once<SupplierPayableDetail>("pay-supplier", clean.idempotencyKey, payload, operatorId) { conn, now ->
            lockReceipt(conn, id)
            val account = readSupplierPayable(conn, id)
                ?: throw NotFoundException("Esta compra no tiene una cuenta por pagar.")
            if (clean.amountCents > account.balanceCents) throw ConflictException("El abono supera el saldo actual de la factura.")
            val movement = sessionId?.let {
                cashService.recordMovement(
                    conn, it,
                    CashMovementRequest("CASH_OUT", clean.amountCents, "Pago a proveedor · ${account.documentNumber} · ${account.supplierName}".take(256), actorId),
                    verifyOperator = true, drawerApproval = operatorId
                )
            }
            conn.prepareStatement(
                "INSERT INTO supplier_payments (id, receipt_id, amount_cents, method, reference, paid_by, created_at, cash_movement_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            ).use {
                it.setString(1, "sp-" + UUID.randomUUID())
                it.setString(2, id)
                it.setLong(3, clean.amountCents)
                it.setString(4, clean.method.name)
                it.setString(5, clean.reference)
                it.setString(6, operatorId)
                it.setLong(7, now)
                it.setString(8, movement)
                it.executeUpdate()
            }
            checkNotNull(readSupplierPayableDetail(conn, id))
        }
    }

    fun receive(request: ReceivePurchaseRequest, operatorId: String): PurchaseDetails {
        validateLineCount(request.lines.size)
        val clean = request.copy(
            documentNumber = requiredText(request.documentNumber.uppercase(Locale.ROOT), "folio del documento", 64),
            notes = optionalText(request.notes, "notas", 1000),
            lines = request.lines.map(::normalizeReceiptLine)
        )
        if (clean.lines.distinctBy { Triple(it.productId, it.lotNumber, it.expirationDate) }.size != clean.lines.size) {
            throw ValidationException("Agrupa las cantidades repetidas del mismo producto y lote en una sola partida.")
        }
        return once<PurchaseDetails>("receive", clean.idempotencyKey, json.encodeToString(clean), operatorId) { conn, now ->
            val supplier = conn.prepareStatement("SELECT * FROM suppliers WHERE id = ? FOR UPDATE").use {
                it.setString(1, clean.supplierId)
                it.executeQuery().use { row ->
                    if (!row.next()) throw NotFoundException("No se encontró el proveedor.")
                    row.supplier()
                }
            }
            if (!supplier.isActive) throw ValidationException("Selecciona un proveedor activo.")

            val inventory = PurchaseInventory(conn, now)
            val products = inventory.lockProducts(clean.lines.map { it.productId })
            val amounts = clean.lines.map { line ->
                val product = products.getValue(line.productId)
                if (!product.receivable) throw ValidationException("${product.name} debe estar activo y controlar existencias como producto individual.")
                product.validateQuantity(line.quantityMillis)
                if (product.perishable && line.lotNumber == null) throw ValidationException("Captura el lote y la caducidad de ${product.name}.")
                lineTotal(line.quantityMillis, line.unitCostCents)
            }
            val receiptId = "rec-" + UUID.randomUUID()
            insertReceipt(conn, receiptId, clean, supplier, amounts.fold(0L, ::checkedPurchaseSum), operatorId, now)

            // Alinea el orden con las operaciones de inventario que bloquean lote antes de saldo.
            val lotIds = clean.lines.indices.sortedWith(
                compareBy({ clean.lines[it].productId }, { clean.lines[it].lotNumber }, { clean.lines[it].expirationDate })
            ).associateWith { inventory.receiveLot(clean.lines[it]) }

            clean.lines.groupBy { it.productId }.toSortedMap().forEach { (productId, lines) ->
                val quantity = lines.fold(0L) { sum, line -> checkedPurchaseSum(sum, line.quantityMillis) }
                inventory.changeStock(products.getValue(productId), quantity)
                inventory.updateCatalogCost(productId, weightedUnitCost(lines, quantity))
            }

            clean.lines.forEachIndexed { index, line ->
                val product = products.getValue(line.productId)
                insertReceiptLine(conn, receiptId, index, line, product, amounts[index], lotIds[index])
                inventory.recordMovement(product.id, line.quantityMillis, "Recepción de compra · ${clean.documentNumber}", receiptId, operatorId)
            }
            readReceipt(conn, receiptId)
        }
    }

    fun returnStock(receiptId: String, request: ReturnPurchaseRequest, operatorId: String): PurchaseReturn {
        validateLineCount(request.lines.size)
        val clean = request.copy(reason = requiredText(request.reason, "motivo de devolución", 256))
        if (clean.lines.distinctBy { it.purchaseLineId }.size != clean.lines.size) {
            throw ValidationException("Incluye cada partida de la compra una sola vez.")
        }

        return once<PurchaseReturn>("return", clean.idempotencyKey, receiptId + "\n" + json.encodeToString(clean), operatorId) { conn, now ->
            lockReceipt(conn, receiptId)
            val originals = readLines(conn, receiptId).associateBy { it.line.id }
            val selections = clean.lines.map { requested ->
                val original = originals[requested.purchaseLineId]
                    ?: throw ValidationException("La partida indicada no pertenece a esta compra.")
                val line = original.line
                val quantity = requested.quantityMillis
                if (quantity <= 0 || (line.baseUnit == "PZA" && quantity % 1000 != 0L)) {
                    throw ValidationException("Captura una cantidad válida para ${line.productName}.")
                }
                if (quantity > line.quantityMillis - line.returnedMillis) {
                    throw StockChangedException("La cantidad de ${line.productName} excede lo que queda por devolver de esta compra.")
                }
                val cumulative = lineTotal(line.returnedMillis + quantity, line.unitCostCents)
                val amount = cumulative - lineTotal(line.returnedMillis, line.unitCostCents)
                original to PurchaseReturnLine(line.id, quantity, amount)
            }
            val inventory = PurchaseInventory(conn, now)
            val products = inventory.lockProducts(selections.map { it.first.line.productId })
            val byProduct = selections.groupBy { it.first.line.productId }.toSortedMap()
            val lots = byProduct.keys.associateWith(inventory::lockLots)

            val returnId = "ret-" + UUID.randomUUID()
            val returnedLines = selections.map { it.second }
            val total = returnedLines.fold(0L) { sum, line -> checkedPurchaseSum(sum, line.totalCents) }
            val operatorName = operatorName(conn, operatorId)
            val result = PurchaseReturn(returnId, receiptId, clean.reason, operatorName, total, now, returnedLines)
            insertReturn(conn, result, operatorId)
            byProduct.forEach { (productId, lines) ->
                val product = products.getValue(productId)
                if (lines.any { it.first.line.baseUnit != product.unit }) {
                    throw ValidationException("La unidad de inventario de ${product.name} cambió desde la compra.")
                }
                val lotQuantities = lots.getValue(productId)
                val lotDeltas = lines.filter { it.first.lotId != null }.groupBy { checkNotNull(it.first.lotId) }
                    .mapValues { (_, rows) -> rows.fold(0L) { sum, row -> checkedPurchaseSum(sum, row.second.quantityMillis) } }
                lotDeltas.toSortedMap().forEach { (lotId, quantity) -> inventory.removeFromLot(lotId, quantity) }
                val remainingInLots = lotQuantities.entries.fold(0L) { sum, (lotId, quantity) ->
                    checkedPurchaseSum(sum, quantity - (lotDeltas[lotId] ?: 0))
                }
                val quantity = lines.fold(0L) { sum, row -> checkedPurchaseSum(sum, row.second.quantityMillis) }
                inventory.changeStock(product, -quantity, remainingInLots)
                lines.forEach { (_, line) ->
                    inventory.recordMovement(productId, -line.quantityMillis, "Devolución a proveedor: ${clean.reason}".take(256), returnId, operatorId)
                }
            }
            result
        }
    }

    private inline fun <reified T> once(
        operation: String, key: String, data: String, operatorId: String,
        crossinline write: (Connection, Long) -> T
    ): T {
        if (!key.matches(Regex("[A-Za-z0-9_-]{1,80}"))) throw ValidationException("La clave de la operación es inválida.")
        val namespaced = "purchase:$operation:$key"
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("$operatorId\n$data".toByteArray()))
        return db.inTransaction { conn ->
            val now = System.currentTimeMillis()
            val claimed = conn.prepareStatement(
                "INSERT INTO idempotency_keys (key_id, request_hash, response_body, created_at) VALUES (?, ?, '', ?) ON CONFLICT DO NOTHING"
            ).use {
                it.setString(1, namespaced)
                it.setString(2, hash)
                it.setLong(3, now)
                it.executeUpdate() == 1
            }
            if (!claimed) {
                return@inTransaction conn.prepareStatement("SELECT request_hash, response_body FROM idempotency_keys WHERE key_id = ?").use {
                    it.setString(1, namespaced)
                    it.executeQuery().use { row ->
                        check(row.next())
                        if (row.getString(1) != hash) throw IdempotencyMismatchException("La clave ya se usó con otros datos de compra.")
                        json.decodeFromString<T>(row.getString(2))
                    }
                }
            }
            val response = write(conn, now)
            conn.prepareStatement("UPDATE idempotency_keys SET response_body = ? WHERE key_id = ?").use {
                it.setString(1, json.encodeToString(response))
                it.setString(2, namespaced)
                it.executeUpdate()
            }
            response
        }
    }

    private fun normalizeReceiptLine(line: ReceivePurchaseLine): ReceivePurchaseLine {
        val lot = line.lotNumber?.let { optionalText(it, "lote", 64).ifBlank { null } }
        val date = line.expirationDate?.trim()?.ifBlank { null }
        if ((lot == null) != (date == null)) throw ValidationException("El lote y la fecha de caducidad se capturan juntos.")
        if (date != null && (date.length != 10 || runCatching { LocalDate.parse(date) }.isFailure)) {
            throw ValidationException("Captura la caducidad con formato AAAA-MM-DD.")
        }
        return line.copy(lotNumber = lot, expirationDate = date)
    }

    private fun lineTotal(quantity: Long, price: Long): Long =
        purchaseLineTotal(quantity, price) ?: throw ValidationException("La cantidad o el costo unitario no es válido.")

    private fun weightedUnitCost(lines: List<ReceivePurchaseLine>, quantity: Long): Long {
        val weighted = lines.fold(BigInteger.ZERO) { sum, line ->
            sum + BigInteger.valueOf(line.quantityMillis) * BigInteger.valueOf(line.unitCostCents)
        }
        return ((weighted + BigInteger.valueOf(quantity / 2)) / BigInteger.valueOf(quantity)).longValueExact()
    }
}

private fun validateLineCount(size: Int) {
    if (size !in 1..200) throw ValidationException("Captura entre 1 y 200 partidas por documento.")
}

private fun requiredText(value: String, label: String, maximum: Int): String =
    optionalText(value, label, maximum).ifBlank { throw ValidationException("Captura $label.") }

private fun optionalText(value: String, label: String, maximum: Int): String =
    value.trim().also { if (it.length > maximum) throw ValidationException("El campo $label supera el máximo de $maximum caracteres.") }
