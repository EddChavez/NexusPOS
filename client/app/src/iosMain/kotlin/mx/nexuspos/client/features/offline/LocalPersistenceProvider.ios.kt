package mx.nexuspos.client.features.offline

private val iosFallbackProvider = InMemoryPersistenceProvider()

actual fun getDefaultPersistenceProvider(): LocalPersistenceProvider = iosFallbackProvider
