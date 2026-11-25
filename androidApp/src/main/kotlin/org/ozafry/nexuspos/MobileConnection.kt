package org.ozafry.nexuspos

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mx.nexuspos.contracts.ApiError
import mx.nexuspos.contracts.mobile.*
import java.net.URI
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

internal class MobileHttpError(val status: Int, message: String) : Exception(message)
internal data class MobileEnrollment(val address: String, val token: String)

internal fun storeAddress(value: String): String? = runCatching {
    val uri = URI(value.trim())
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank())
    require(uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path in listOf("/", ""))
    require(uri.port == -1 || uri.port in 1..65535)
    URI("https", null, uri.host, uri.port, null, null, null).toASCIIString()
}.getOrNull()

internal class MobileApi(private val address: String) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun pair(code: String, name: String): MobileDeviceCredential =
        request("pairing/claim", method = "POST", payload = json.encodeToString(MobilePairRequest(code, name)))

    suspend fun login(token: String, pin: String): MobileSession =
        request("session", token, "POST", json.encodeToString(MobileSessionRequest(pin)))

    suspend fun logout(token: String) { exchange("session", token, "DELETE", null) }

    suspend fun overview(token: String): MobileOverview = request("overview", token)

    suspend fun products(token: String, value: String, barcode: Boolean): List<MobileProduct> =
        request("products?${if (barcode) "barcode" else "query"}=${encoded(value)}", token)

    suspend fun shifts(token: String, date: String?, register: String?): MobileShifts =
        request("shifts?" + parameters("date" to date, "registerId" to register), token)

    suspend fun shiftSales(token: String, id: String, folio: Long?, before: Long?): MobileShiftSales =
        request("shifts/${encoded(id)}/sales?" + parameters("folio" to folio?.toString(), "before" to before?.toString()), token)

    private fun parameters(vararg values: Pair<String, String?>): String =
        values.mapNotNull { (key, value) -> value?.let { "$key=${encoded(it)}" } }.joinToString("&")

    private fun encoded(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private suspend inline fun <reified T> request(path: String, token: String? = null, method: String = "GET", payload: String? = null): T =
        json.decodeFromString(exchange(path, token, method, payload))

    private suspend fun exchange(path: String, token: String?, method: String, payload: String?): String = withContext(Dispatchers.IO) {
        val connection = URI("$address/api/v1/mobile/$path").toURL().openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }
                val message = body?.let { runCatching { json.decodeFromString<ApiError>(it).message }.getOrNull() }
                throw MobileHttpError(status, message ?: "El servidor respondió con código $status.")
            }
            connection.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }
}

internal class EnrollmentStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_mobile_enrollment", Context.MODE_PRIVATE)

    fun load(): MobileEnrollment? = runCatching {
        val address = prefs.getString("address", null)?.let(::storeAddress) ?: return null
        val iv = Base64.decode(prefs.getString("iv", null) ?: return null, Base64.NO_WRAP)
        val encrypted = Base64.decode(prefs.getString("token", null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(address.toByteArray())
        MobileEnrollment(address, cipher.doFinal(encrypted).toString(Charsets.UTF_8))
    }.getOrElse {
        clear()
        null
    }

    fun save(enrollment: MobileEnrollment) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(enrollment.address.toByteArray())
        val token = cipher.doFinal(enrollment.token.toByteArray())
        check(prefs.edit().putString("address", enrollment.address)
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token", Base64.encodeToString(token, Base64.NO_WRAP)).commit())
    }

    fun clear() { prefs.edit().clear().apply() }

    private fun key(): SecretKey {
        val alias = "nexuspos-mobile-device"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }
}
