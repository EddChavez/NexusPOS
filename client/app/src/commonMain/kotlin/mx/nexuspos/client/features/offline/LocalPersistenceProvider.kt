package mx.nexuspos.client.features.offline

interface LocalPersistenceProvider {
    fun readText(fileName: String): String?
    fun writeText(fileName: String, content: String)
    fun exists(fileName: String): Boolean
    fun delete(fileName: String): Boolean
}

class InMemoryPersistenceProvider : LocalPersistenceProvider {
    private val files = mutableMapOf<String, String>()

    override fun readText(fileName: String): String? = files[fileName]

    override fun writeText(fileName: String, content: String) {
        files[fileName] = content
    }

    override fun exists(fileName: String): Boolean = files.containsKey(fileName)

    override fun delete(fileName: String): Boolean = files.remove(fileName) != null

    fun clear() {
        files.clear()
    }
}

expect fun getDefaultPersistenceProvider(): LocalPersistenceProvider
