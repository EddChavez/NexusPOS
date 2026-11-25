package mx.nexuspos.client.features.offline

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FilePersistenceProvider(
    private val baseDir: File = defaultStorageDir()
) : LocalPersistenceProvider {

    companion object {
        fun defaultStorageDir(): File {
            val userHome = System.getProperty("user.home") ?: "."
            val dir = File(userHome, ".nexuspos")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    }

    override fun readText(fileName: String): String? {
        val file = File(baseDir, fileName)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    override fun writeText(fileName: String, content: String) {
        Files.createDirectories(baseDir.toPath())
        val target = File(baseDir, fileName).toPath()
        val temporary = Files.createTempFile(baseDir.toPath(), ".nexuspos-", ".tmp")
        try {
            temporary.toFile().outputStream().use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override fun exists(fileName: String): Boolean {
        return File(baseDir, fileName).exists()
    }

    override fun delete(fileName: String): Boolean {
        val file = File(baseDir, fileName)
        return if (file.exists()) file.delete() else false
    }
}

actual fun getDefaultPersistenceProvider(): LocalPersistenceProvider {
    val isTest = System.getProperty("nexuspos.test.inmemory") == "true"
    return if (isTest) InMemoryPersistenceProvider() else FilePersistenceProvider()
}
