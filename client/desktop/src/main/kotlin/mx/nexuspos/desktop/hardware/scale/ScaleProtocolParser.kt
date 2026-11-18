package mx.nexuspos.desktop.hardware.scale

import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.scale.ScaleProtocol
import java.util.Locale
import kotlin.math.abs

interface ScaleProtocolParser {
    fun parseRawReading(raw: String): ScaleReading?
    fun getPollingCommand(): ByteArray?
}

/**
 * Parser para básculas Torrey (modelos L-EQ, MFQ, PCR, W-Label).
 * Responde a comando de polling 'P' (0x50) o transmisión continua.
 */
class TorreyScaleProtocolParser : ScaleProtocolParser {

    private var lastWeightKg: Double? = null
    private var identicalSampleCount: Int = 0

    override fun getPollingCommand(): ByteArray {
        return byteArrayOf('P'.code.toByte())
    }

    override fun parseRawReading(raw: String): ScaleReading? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val isUnstable = trimmed.contains("US", ignoreCase = true) || trimmed.contains("MOV", ignoreCase = true)

        val clean = trimmed
            .replace("ST,", "")
            .replace("US,", "")
            .replace("GS,", "")
            .replace("NT,", "")
            .replace("kg", "", ignoreCase = true)
            .replace("k", "", ignoreCase = true)
            .trim()

        val parsedDouble = clean.toDoubleOrNull() ?: parseWithRegex(clean) ?: return null
        val weightKg = (parsedDouble * 1000.0).toLong() / 1000.0 // 3 decimales
        val weightText = String.format(Locale.US, "%.3f kg", abs(weightKg))

        val status = when {
            abs(weightKg) <= 0.005 -> ScaleStatus.ZERO
            isUnstable -> ScaleStatus.READING
            else -> determineStability(weightKg)
        }

        return ScaleReading(weightText = weightText, status = status)
    }

    private fun determineStability(currentWeightKg: Double): ScaleStatus {
        val prev = lastWeightKg
        lastWeightKg = currentWeightKg
        return if (prev != null && abs(prev - currentWeightKg) < 0.003) {
            identicalSampleCount++
            if (identicalSampleCount >= 1) ScaleStatus.STABLE else ScaleStatus.READING
        } else {
            identicalSampleCount = 0
            ScaleStatus.READING
        }
    }

    private fun parseWithRegex(str: String): Double? {
        val match = Regex("""([+-]?\s*\d+(?:\.\d+)?)""").find(str) ?: return null
        return match.groupValues[1].replace(" ", "").toDoubleOrNull()
    }
}

/**
 * Parser para básculas Rhino (modelos BAR-8, BAB-30, I-QY).
 */
class RhinoScaleProtocolParser : ScaleProtocolParser {

    private var lastWeightKg: Double? = null

    override fun getPollingCommand(): ByteArray? {
        // Rhino en modo continuo no requiere polling, en modo petición envía 'W'
        return byteArrayOf('W'.code.toByte())
    }

    override fun parseRawReading(raw: String): ScaleReading? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val isUnstable = trimmed.startsWith("US") || trimmed.contains("DYN", ignoreCase = true)

        val clean = trimmed
            .replace("WN", "")
            .replace("US", "")
            .replace("ST", "")
            .replace("kg", "", ignoreCase = true)
            .trim()

        val match = Regex("""([+-]?\s*\d+(?:\.\d+)?)""").find(clean) ?: return null
        val weight = match.groupValues[1].replace(" ", "").toDoubleOrNull() ?: return null

        val weightKg = (weight * 1000.0).toLong() / 1000.0
        val weightText = String.format(Locale.US, "%.3f kg", abs(weightKg))

        val status = when {
            abs(weightKg) <= 0.005 -> ScaleStatus.ZERO
            isUnstable -> ScaleStatus.READING
            else -> {
                val prev = lastWeightKg
                lastWeightKg = weightKg
                if (prev != null && abs(prev - weightKg) < 0.003) ScaleStatus.STABLE else ScaleStatus.READING
            }
        }

        return ScaleReading(weightText = weightText, status = status)
    }
}

/**
 * Parser genérico con heurística de expresiones regulares y conversión de unidades.
 */
class GenericScaleProtocolParser : ScaleProtocolParser {

    private var lastWeightKg: Double? = null

    override fun getPollingCommand(): ByteArray? = null

    override fun parseRawReading(raw: String): ScaleReading? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        // Detecta número y unidad opcional
        val regex = Regex("""([+-]?\s*\d+(?:\.\d+)?)\s*(kg|g|gr|lb)?""", RegexOption.IGNORE_CASE)
        val match = regex.find(trimmed) ?: return null

        val numStr = match.groupValues[1].replace(" ", "")
        val rawNum = numStr.toDoubleOrNull() ?: return null
        val unit = match.groupValues.getOrNull(2)?.lowercase() ?: "kg"

        var weightKg = when (unit) {
            "g", "gr" -> rawNum / 1000.0
            "lb" -> rawNum * 0.45359237
            else -> rawNum
        }
        weightKg = (weightKg * 1000.0).toLong() / 1000.0

        val weightText = String.format(Locale.US, "%.3f kg", abs(weightKg))
        val status = when {
            abs(weightKg) <= 0.005 -> ScaleStatus.ZERO
            else -> {
                val prev = lastWeightKg
                lastWeightKg = weightKg
                if (prev != null && abs(prev - weightKg) < 0.003) ScaleStatus.STABLE else ScaleStatus.READING
            }
        }

        return ScaleReading(weightText = weightText, status = status)
    }
}

object ScaleProtocolParserFactory {
    fun create(protocol: ScaleProtocol): ScaleProtocolParser {
        return when (protocol) {
            ScaleProtocol.TORREY -> TorreyScaleProtocolParser()
            ScaleProtocol.RHINO -> RhinoScaleProtocolParser()
            ScaleProtocol.GENERIC -> GenericScaleProtocolParser()
            ScaleProtocol.SIMULATOR -> GenericScaleProtocolParser()
            ScaleProtocol.NONE -> GenericScaleProtocolParser()
        }
    }
}
