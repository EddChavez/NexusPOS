package mx.nexuspos.server.modules.system

import mx.nexuspos.contracts.system.CommercialPreferences
import mx.nexuspos.contracts.system.CatalogRounding
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.contracts.system.SettingsPreferencesPatch
import mx.nexuspos.contracts.system.SystemInfoDto
import mx.nexuspos.server.persistence.DatabaseFactory
import mx.nexuspos.server.platform.ValidationException

class SettingsService(private val db: DatabaseFactory) {

    fun getSettings(): SettingsDto {
        return db.inTransaction { conn ->
            val stmt = conn.prepareStatement("SELECT setting_key, setting_value FROM settings")
            val rs = stmt.executeQuery()
            var storeName = "Mi Tiendita de Abarrotes"
            var storeAddress = "Av. Principal #100"
            var storePhone = "555-987-6543"
            var storeTaxId = ""
            var ticketHeader = "¡Gracias por su compra!"
            var ticketFooter = "Conserve su ticket para cualquier aclaración."
            var allowNegativeStock = true
            var currencySymbol = "$"
            var blindCashCountEnabled = false
            var maxCashierDiscountPercent = 10.0
            var loyaltyEnabled = true
            var loyaltyCashbackPercent = 1.0
            var loyaltyMinRedeemCents = 0L
            var whatsappCountryCode = "52"
            var whatsappBankDetails = ""
            var whatsappCustomFooter = "¡Gracias por su preferencia!"

            var commerce = CommercialPreferences()
            while (rs.next()) {
                when (rs.getString("setting_key")) {
                    "store_name" -> storeName = rs.getString("setting_value")
                    "store_address" -> storeAddress = rs.getString("setting_value")
                    "store_phone" -> storePhone = rs.getString("setting_value")
                    "store_tax_id" -> storeTaxId = rs.getString("setting_value")
                    "ticket_header" -> ticketHeader = rs.getString("setting_value")
                    "ticket_footer" -> ticketFooter = rs.getString("setting_value")
                    "allow_negative_stock" -> allowNegativeStock = rs.getString("setting_value").toBoolean()
                    "currency_symbol" -> currencySymbol = rs.getString("setting_value")
                    "blind_cash_count_enabled" -> blindCashCountEnabled = rs.getString("setting_value").toBoolean()
                    "max_cashier_discount_percent" -> maxCashierDiscountPercent = rs.getString("setting_value").toDoubleOrNull() ?: 10.0
                    "loyalty_enabled" -> loyaltyEnabled = rs.getString("setting_value").toBoolean()
                    "loyalty_cashback_percent" -> loyaltyCashbackPercent = rs.getString("setting_value").toDoubleOrNull() ?: 1.0
                    "loyalty_min_redeem_cents" -> loyaltyMinRedeemCents = rs.getString("setting_value").toLongOrNull() ?: 0L
                    "whatsapp_country_code" -> whatsappCountryCode = rs.getString("setting_value")
                    "whatsapp_bank_details" -> whatsappBankDetails = rs.getString("setting_value")
                    "whatsapp_custom_footer" -> whatsappCustomFooter = rs.getString("setting_value")
                    "quick_sale_enabled" -> commerce = commerce.copy(quickSaleEnabled = rs.getString("setting_value").toBoolean())
                    "require_opening_fund" -> commerce = commerce.copy(requireOpeningFund = rs.getString("setting_value").toBoolean())
                    "credit_sales_enabled" -> commerce = commerce.copy(creditSalesEnabled = rs.getString("setting_value").toBoolean())
                    "credit_payment_methods" -> commerce = commerce.copy(creditPaymentMethods = rs.getString("setting_value").split(',').toSet())
                    "default_vat_percent" -> commerce = commerce.copy(defaultVatPercent = rs.getString("setting_value").toIntOrNull() ?: 0)
                    "catalog_rounding" -> commerce = commerce.copy(catalogRounding = CatalogRounding.entries.firstOrNull { it.name == rs.getString("setting_value") } ?: CatalogRounding.NONE)
                    "cash_drawer_limit_cents" -> commerce = commerce.copy(cashDrawerLimitCents = rs.getString("setting_value").toLongOrNull())
                    "allow_shift_handoff" -> commerce = commerce.copy(allowShiftHandoff = rs.getString("setting_value").toBoolean())
                    "compatible_barcode" -> commerce = commerce.copy(matchBarcodeWithoutLeadingZeros = rs.getString("setting_value").toBoolean())
                }
            }
            rs.close()
            stmt.close()

            SettingsDto(
                storeName = storeName,
                storeAddress = storeAddress,
                storePhone = storePhone,
                storeTaxId = storeTaxId,
                ticketHeader = ticketHeader,
                ticketFooter = ticketFooter,
                allowNegativeStock = allowNegativeStock,
                currencySymbol = currencySymbol,
                blindCashCountEnabled = blindCashCountEnabled,
                maxCashierDiscountPercent = maxCashierDiscountPercent,
                loyaltyEnabled = loyaltyEnabled,
                loyaltyCashbackPercent = loyaltyCashbackPercent,
                loyaltyMinRedeemCents = loyaltyMinRedeemCents,
                whatsappCountryCode = whatsappCountryCode,
                whatsappBankDetails = whatsappBankDetails,
                whatsappCustomFooter = whatsappCustomFooter,
                commerce = commerce
            )
        }
    }

    fun updateCommerce(preferences: CommercialPreferences): CommercialPreferences {
        if (preferences.defaultVatPercent !in setOf(0, 8, 16)) throw ValidationException("Selecciona IVA de 0, 8 o 16%.")
        if ((preferences.cashDrawerLimitCents ?: 1) <= 0) {
            throw ValidationException("El límite de efectivo debe ser mayor a cero.")
        }
        val methods = preferences.creditPaymentMethods
        if (methods.isEmpty() || !setOf("CASH", "TRANSFER", "CARD").containsAll(methods)) {
            throw ValidationException("Habilita al menos un método válido para abonos.")
        }
        return updateSettingValues(mapOf(
            "quick_sale_enabled" to preferences.quickSaleEnabled.toString(),
            "require_opening_fund" to preferences.requireOpeningFund.toString(),
            "credit_sales_enabled" to preferences.creditSalesEnabled.toString(),
            "credit_payment_methods" to methods.sorted().joinToString(","),
            "default_vat_percent" to preferences.defaultVatPercent.toString(),
            "catalog_rounding" to preferences.catalogRounding.name,
            "compatible_barcode" to preferences.matchBarcodeWithoutLeadingZeros.toString(),
            "allow_shift_handoff" to preferences.allowShiftHandoff.toString(),
            "cash_drawer_limit_cents" to (preferences.cashDrawerLimitCents?.toString() ?: "")
        )).commerce
    }

    fun updateSettings(settings: SettingsDto): SettingsDto = updateSettingValues(
        mapOf(
            "store_name" to settings.storeName,
            "store_address" to settings.storeAddress,
            "store_phone" to settings.storePhone,
            "store_tax_id" to settings.storeTaxId,
            "ticket_header" to settings.ticketHeader,
            "ticket_footer" to settings.ticketFooter,
            "allow_negative_stock" to settings.allowNegativeStock.toString(),
            "currency_symbol" to settings.currencySymbol,
            "blind_cash_count_enabled" to settings.blindCashCountEnabled.toString(),
            "max_cashier_discount_percent" to settings.maxCashierDiscountPercent.toString(),
            "loyalty_enabled" to settings.loyaltyEnabled.toString(),
            "loyalty_cashback_percent" to settings.loyaltyCashbackPercent.toString(),
            "loyalty_min_redeem_cents" to settings.loyaltyMinRedeemCents.toString(),
            "whatsapp_country_code" to settings.whatsappCountryCode,
            "whatsapp_bank_details" to settings.whatsappBankDetails,
            "whatsapp_custom_footer" to settings.whatsappCustomFooter
        )
    )

    fun updatePreferences(patch: SettingsPreferencesPatch): SettingsDto {
        val values = linkedMapOf<String, String>()
        patch.store?.let {
            values.putAll(
                mapOf(
                    "store_name" to it.storeName,
                    "store_address" to it.storeAddress,
                    "store_phone" to it.storePhone,
                    "store_tax_id" to it.storeTaxId,
                    "ticket_header" to it.ticketHeader,
                    "ticket_footer" to it.ticketFooter,
                    "currency_symbol" to it.currencySymbol,
                    "whatsapp_country_code" to it.whatsappCountryCode,
                    "whatsapp_bank_details" to it.whatsappBankDetails,
                    "whatsapp_custom_footer" to it.whatsappCustomFooter,
                )
            )
        }
        patch.policies?.let {
            if (it.maxCashierDiscountPercent !in 0.0..100.0 || it.loyaltyCashbackPercent !in 0.0..100.0 ||
                it.loyaltyMinRedeemCents < 0
            ) {
                throw ValidationException("Los porcentajes deben estar entre 0 y 100 y el importe de canje debe ser positivo o cero.")
            }
            values.putAll(
                mapOf(
                    "allow_negative_stock" to it.allowNegativeStock.toString(),
                    "blind_cash_count_enabled" to it.blindCashCountEnabled.toString(),
                    "max_cashier_discount_percent" to it.maxCashierDiscountPercent.toString(),
                    "loyalty_enabled" to it.loyaltyEnabled.toString(),
                    "loyalty_cashback_percent" to it.loyaltyCashbackPercent.toString(),
                    "loyalty_min_redeem_cents" to it.loyaltyMinRedeemCents.toString(),
                )
            )
        }
        return updateSettingValues(values)
    }

    private fun updateSettingValues(values: Map<String, String>): SettingsDto {
        db.inTransaction { conn ->
            val updateStmt = conn.prepareStatement("UPDATE settings SET setting_value = ? WHERE setting_key = ?")
            val insertStmt = conn.prepareStatement("INSERT INTO settings (setting_key, setting_value) VALUES (?, ?)")
            for ((k, v) in values) {
                updateStmt.setString(1, v)
                updateStmt.setString(2, k)
                val rows = updateStmt.executeUpdate()
                if (rows == 0) {
                    insertStmt.setString(1, k)
                    insertStmt.setString(2, v)
                    insertStmt.executeUpdate()
                }
            }
            updateStmt.close()
            insertStmt.close()
        }
        return getSettings()
    }

    fun getSystemInfo(): SystemInfoDto {
        return SystemInfoDto(
            appVersion = "2.0.0-lan",
            apiVersion = "v1",
            serverTime = System.currentTimeMillis(),
            dbEngine = "PostgreSQL 16",
            connectedDevices = 1
        )
    }
}
