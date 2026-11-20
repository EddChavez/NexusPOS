package mx.nexuspos.client.features.offline

import java.io.File

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
        return if (file.exists()) {
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    override fun writeText(fileName: String, content: String) {
        try {
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            val file = File(baseDir, fileName)
            file.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            System.err.println("Error saving local file $fileName: ${e.message}")
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
