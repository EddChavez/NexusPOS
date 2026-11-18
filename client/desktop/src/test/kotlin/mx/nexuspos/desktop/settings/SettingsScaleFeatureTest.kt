package mx.nexuspos.desktop.settings

import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.scale.ScaleConfig
import mx.nexuspos.client.features.scale.ScaleProtocol
import mx.nexuspos.client.features.scale.ScaleUiConfig
import mx.nexuspos.client.features.settings.SettingsTab
import mx.nexuspos.client.features.settings.SettingsViewModel
import mx.nexuspos.desktop.hardware.scale.ScaleConfigRepository
import mx.nexuspos.desktop.hardware.scale.ScaleManager
import mx.nexuspos.desktop.hardware.scale.SimulatedScaleDriver
import java.io.File
import kotlin.test.*

class SettingsScaleFeatureTest {

    private class FakeApiClient : ApiClient("http://localhost:8080")

    @Test
    fun testScaleSettingsLoadAndSave() {
        val tempFile = File.createTempFile("scale_test_", ".properties")
        tempFile.deleteOnExit()

        val repo = ScaleConfigRepository(tempFile)
        val initial = ScaleConfig(
            enabled = true,
            portName = "COM3",
            baudRate = 9600,
            protocol = ScaleProtocol.TORREY,
            autoAcceptStableWeight = true
        )
        repo.save(initial)

        val vm = SettingsViewModel(FakeApiClient())
        vm.scaleConfigLoader = {
            val cfg = repo.load()
            ScaleUiConfig(
                enabled = cfg.enabled,
                portName = cfg.portName,
                baudRate = cfg.baudRate,
                protocol = cfg.protocol.code,
                autoAcceptStableWeight = cfg.autoAcceptStableWeight
            )
        }
        vm.scaleConfigSaver = { uiCfg ->
            repo.save(
                ScaleConfig(
                    enabled = uiCfg.enabled,
                    portName = uiCfg.portName,
                    baudRate = uiCfg.baudRate,
                    protocol = ScaleProtocol.fromString(uiCfg.protocol),
                    autoAcceptStableWeight = uiCfg.autoAcceptStableWeight
                )
            )
        }
        vm.serialPortsProvider = { listOf("COM1", "COM3", "SIMULADOR") }

        // Cargar
        vm.loadScaleConfig()
        var s = vm.uiState.value
        assertTrue(s.scaleEnabled)
        assertEquals("COM3", s.scalePortName)
        assertEquals("TORREY", s.scaleProtocol)
        assertTrue(s.scaleAutoAcceptWeight)
        assertEquals(listOf("COM1", "COM3", "SIMULADOR"), s.availableScalePorts)

        // Modificar
        vm.onScalePortNameChanged("SIMULADOR")
        vm.onScaleProtocolChanged("SIMULATOR")
        vm.onScaleAutoAcceptWeightChanged(false)

        vm.saveScaleConfig()

        // Verificar persistencia en archivo
        val reloaded = repo.load()
        assertEquals("SIMULADOR", reloaded.portName)
        assertEquals(ScaleProtocol.SIMULATOR, reloaded.protocol)
        assertFalse(reloaded.autoAcceptStableWeight)
    }

    @Test
    fun testScaleDiagnosticTestCall() {
        val vm = SettingsViewModel(FakeApiClient(), scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        val simDriver = SimulatedScaleDriver(initialWeightKg = 1.250, initialStatus = ScaleStatus.STABLE)

        vm.testScaleAction = {
            Result.success(simDriver.getCurrentReading())
        }

        vm.testScale()
        val s = vm.uiState.value
        assertNotNull(s.scaleTestResult)
        assertTrue(s.isScaleTestSuccess)
        assertTrue(s.scaleTestResult!!.contains("1.250 kg"))
        assertTrue(s.scaleTestResult!!.contains("ESTABLE"))
    }
}
