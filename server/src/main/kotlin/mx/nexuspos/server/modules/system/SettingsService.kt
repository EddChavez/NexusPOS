package mx.nexuspos.server.modules.system

import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.contracts.system.SystemInfoDto
import mx.nexuspos.server.persistence.DatabaseFactory

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
                whatsappCustomFooter = whatsappCustomFooter
            )
        }
    }

    fun updateSettings(settings: SettingsDto): SettingsDto {
        db.inTransaction { conn ->
            val updateStmt = conn.prepareStatement("UPDATE settings SET setting_value = ? WHERE setting_key = ?")
            val insertStmt = conn.prepareStatement("INSERT INTO settings (setting_key, setting_value) VALUES (?, ?)")

            val map = mapOf(
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
            for ((k, v) in map) {
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
