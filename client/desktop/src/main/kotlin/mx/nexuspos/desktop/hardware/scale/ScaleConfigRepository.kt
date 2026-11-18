package mx.nexuspos.desktop.hardware.scale

import mx.nexuspos.client.features.scale.ScaleConfig
import mx.nexuspos.client.features.scale.ScaleProtocol
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class ScaleConfigRepository(
    private val configFile: File = defaultConfigFile()
) {
    companion object {
        fun defaultConfigFile(): File {
            val userHome = System.getProperty("user.home") ?: "."
            val dir = File(userHome, ".nexuspos")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return File(dir, "scale.properties")
        }
    }

    fun load(): ScaleConfig {
        if (!configFile.exists()) {
            return ScaleConfig()
        }

        return try {
            val props = Properties()
            FileInputStream(configFile).use { props.load(it) }

            val enabled = props.getProperty("scale.enabled", "true").toBoolean()
            val portName = props.getProperty("scale.portName", "AUTO").trim()
            val baudRate = props.getProperty("scale.baudRate", "9600").toIntOrNull() ?: 9600
            val protocolStr = props.getProperty("scale.protocol", "TORREY")
            val protocol = ScaleProtocol.fromString(protocolStr)
            val autoAccept = props.getProperty("scale.autoAcceptStableWeight", "false").toBoolean()
            val pollInterval = props.getProperty("scale.pollIntervalMs", "250").toLongOrNull() ?: 250L

            ScaleConfig(
                enabled = enabled,
                portName = if (portName.isBlank()) "AUTO" else portName,
                baudRate = baudRate,
                protocol = protocol,
                autoAcceptStableWeight = autoAccept,
                pollIntervalMs = pollInterval
            )
        } catch (e: Exception) {
            ScaleConfig()
        }
    }

    fun save(config: ScaleConfig) {
        try {
            configFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }
            val props = Properties()
            props.setProperty("scale.enabled", config.enabled.toString())
            props.setProperty("scale.portName", config.portName)
            props.setProperty("scale.baudRate", config.baudRate.toString())
            props.setProperty("scale.protocol", config.protocol.code)
            props.setProperty("scale.autoAcceptStableWeight", config.autoAcceptStableWeight.toString())
            props.setProperty("scale.pollIntervalMs", config.pollIntervalMs.toString())

            FileOutputStream(configFile).use { out ->
                props.store(out, "NexusPOS Scale Configuration")
            }
        } catch (e: Exception) {
            // Log or ignore on write error
        }
    }
}
