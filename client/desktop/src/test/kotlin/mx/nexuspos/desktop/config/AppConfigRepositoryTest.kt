package mx.nexuspos.desktop.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.settings.NetworkUiConfig
import mx.nexuspos.client.features.settings.SettingsTab
import mx.nexuspos.client.features.settings.SettingsViewModel
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class AppConfigRepositoryTest {

    private lateinit var tempFile: File
    private lateinit var repository: AppConfigRepository

    @BeforeTest
    fun setUp() {
        tempFile = Files.createTempFile("nexus_test_config_", ".properties").toFile()
        tempFile.delete() // start empty
        repository = AppConfigRepository(tempFile)
    }

    @AfterTest
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun testDefaultConfigWhenFileDoesNotExist() {
        assertFalse(tempFile.exists())
        val config = repository.load()

        assertEquals("localhost", config.serverHost)
        assertEquals(8080, config.serverPort)
        assertEquals("http", config.serverScheme)
        assertEquals("caja-1", config.registerId)
        assertEquals("Caja Principal", config.registerName)
        assertEquals(3000, config.connectTimeoutMs)
        assertEquals("http://localhost:8080", config.baseUrl)
    }

    @Test
    fun testSaveAndReloadCustomConfiguration() {
        val custom = AppConfig(
            serverHost = "192.168.1.150",
            serverPort = 9090,
            serverScheme = "http",
            registerId = "caja-2",
            registerName = "Caja Secundaria Abarrotes",
            connectTimeoutMs = 5000
        )

        repository.save(custom)
        assertTrue(tempFile.exists())

        val reloaded = repository.load()
        assertEquals("192.168.1.150", reloaded.serverHost)
        assertEquals(9090, reloaded.serverPort)
        assertEquals("http", reloaded.serverScheme)
        assertEquals("caja-2", reloaded.registerId)
        assertEquals("Caja Secundaria Abarrotes", reloaded.registerName)
        assertEquals(5000, reloaded.connectTimeoutMs)
        assertEquals("http://192.168.1.150:9090", reloaded.baseUrl)
    }

    @Test
    fun testSettingsViewModelNetworkConfigurationFlow() {
        val fakeApiClient = object : ApiClient(baseUrl = "http://localhost:8080") {
            override suspend fun checkHealth(): Boolean = true
        }
        val vm = SettingsViewModel(fakeApiClient, CoroutineScope(Dispatchers.Unconfined))

        var savedNetworkConfig: NetworkUiConfig? = null
        vm.networkConfigLoader = {
            NetworkUiConfig(
                serverHost = "192.168.1.80",
                serverPort = 8080,
                serverScheme = "http",
                registerId = "caja-03",
                registerName = "Caja Bodega"
            )
        }
        vm.networkConfigSaver = { cfg ->
            savedNetworkConfig = cfg
        }

        var testedHost: String? = null
        var testedPort: Int? = null
        vm.testNetworkAction = { host, port ->
            testedHost = host
            testedPort = port
            Result.success("HTTP 200 OK")
        }

        // Switch to Network tab
        vm.selectTab(SettingsTab.NETWORK)

        assertEquals("192.168.1.80", vm.uiState.value.serverHost)
        assertEquals("8080", vm.uiState.value.serverPort)
        assertEquals("caja-03", vm.uiState.value.registerId)
        assertEquals("Caja Bodega", vm.uiState.value.registerName)

        // Modify values
        vm.onServerHostChanged("192.168.1.99")
        vm.onServerPortChanged("8888")
        vm.onRegisterIdChanged("caja-central")
        vm.onRegisterNameChanged("Caja Maestra")

        // Test network connection
        vm.testNetwork()
        assertEquals("192.168.1.99", testedHost)
        assertEquals(8888, testedPort)
        assertTrue(vm.uiState.value.isNetworkTestSuccess)
        assertTrue(vm.uiState.value.networkTestResult?.contains("Conexión exitosa") == true)

        // Save
        vm.saveNetworkConfig()
        val saved = savedNetworkConfig
        assertNotNull(saved)
        assertEquals("192.168.1.99", saved.serverHost)
        assertEquals(8888, saved.serverPort)
        assertEquals("caja-central", saved.registerId)
        assertEquals("Caja Maestra", saved.registerName)
        assertEquals("Configuración de red y servidor guardada correctamente.", vm.uiState.value.successBanner)
    }
}
