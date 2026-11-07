package mx.nexuspos.desktop.config

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class AppConfigRepository(
    private val configFile: File = defaultConfigFile()
) {
    companion object {
        fun defaultConfigFile(): File {
            val userHome = System.getProperty("user.home") ?: "."
            val dir = File(userHome, ".nexuspos")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return File(dir, "config.properties")
        }
    }

    fun load(): AppConfig {
        if (!configFile.exists()) {
            return AppConfig()
        }

        return try {
            val props = Properties()
            FileInputStream(configFile).use { props.load(it) }

            val host = props.getProperty("server.host", "localhost").trim()
            val port = props.getProperty("server.port", "8080").trim().toIntOrNull() ?: 8080
            val scheme = props.getProperty("server.scheme", "http").trim()
            val registerId = props.getProperty("register.id", "caja-1").trim()
            val registerName = props.getProperty("register.name", "Caja Principal").trim()
            val timeout = props.getProperty("server.connectTimeoutMs", "3000").trim().toIntOrNull() ?: 3000

            AppConfig(
                serverHost = if (host.isBlank()) "localhost" else host,
                serverPort = port,
                serverScheme = if (scheme.isBlank()) "http" else scheme,
                registerId = if (registerId.isBlank()) "caja-1" else registerId,
                registerName = if (registerName.isBlank()) "Caja Principal" else registerName,
                connectTimeoutMs = timeout
            )
        } catch (e: Exception) {
            AppConfig()
        }
    }

    fun save(config: AppConfig) {
        try {
            configFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }
            val props = Properties()
            props.setProperty("server.host", config.serverHost)
            props.setProperty("server.port", config.serverPort.toString())
            props.setProperty("server.scheme", config.serverScheme)
            props.setProperty("register.id", config.registerId)
            props.setProperty("register.name", config.registerName)
            props.setProperty("server.connectTimeoutMs", config.connectTimeoutMs.toString())

            FileOutputStream(configFile).use { out ->
                props.store(out, "NexusPOS Configuration - Terminal & Server LAN")
            }
        } catch (e: Exception) {
            System.err.println("Error saving config to ${configFile.absolutePath}: ${e.message}")
        }
    }
}
