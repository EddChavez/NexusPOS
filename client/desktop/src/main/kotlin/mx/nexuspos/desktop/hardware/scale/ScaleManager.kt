package mx.nexuspos.desktop.hardware.scale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.scale.ScaleConfig
import mx.nexuspos.client.features.scale.ScaleProtocol

class ScaleManager(
    private val configRepository: ScaleConfigRepository = ScaleConfigRepository(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var currentConfig: ScaleConfig = configRepository.load()
    private var currentDriver: ScaleDriver = NoneScaleDriver()

    private val _readingFlow = MutableStateFlow(ScaleReading("0.000 kg", ScaleStatus.DISCONNECTED))
    val readingFlow: StateFlow<ScaleReading> = _readingFlow.asStateFlow()

    init {
        initialize(currentConfig)
    }

    fun getConfig(): ScaleConfig = currentConfig

    fun initialize(config: ScaleConfig = currentConfig) {
        currentConfig = config
        currentDriver.disconnect()

        currentDriver = when {
            !config.enabled || config.protocol == ScaleProtocol.NONE -> {
                NoneScaleDriver()
            }
            config.protocol == ScaleProtocol.SIMULATOR || config.portName.contains("SIMULATOR", ignoreCase = true) -> {
                SimulatedScaleDriver()
            }
            else -> {
                JSerialCommScaleDriver(scope)
            }
        }

        currentDriver.connect(config)

        scope.launch {
            currentDriver.readingFlow.collect { reading ->
                _readingFlow.value = reading
            }
        }
    }

    fun updateConfig(newConfig: ScaleConfig): Result<Unit> {
        configRepository.save(newConfig)
        initialize(newConfig)
        return Result.success(Unit)
    }

    fun getAvailablePorts(): List<String> {
        val osPorts = try {
            JSerialCommScaleDriver().getAvailablePorts()
        } catch (_: Exception) {
            emptyList()
        }
        val list = mutableListOf("AUTO")
        list.addAll(osPorts)
        list.add("SIMULADOR")
        return list.distinct()
    }

    fun tare(): Result<Unit> = currentDriver.tare()

    fun zero(): Result<Unit> = currentDriver.zero()

    fun isConnected(): Boolean = currentDriver.isConnected()

    fun getCurrentReading(): ScaleReading = currentDriver.getCurrentReading()

    fun getDriver(): ScaleDriver = currentDriver
}
