package mx.nexuspos.desktop.hardware.scale

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.nexuspos.client.features.header.ScaleReading
import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.scale.ScaleConfig
import mx.nexuspos.client.features.scale.ScaleProtocol
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.abs

interface ScaleDriver {
    fun getAvailablePorts(): List<String>
    fun connect(config: ScaleConfig): Result<Unit>
    fun disconnect()
    fun isConnected(): Boolean
    fun getCurrentReading(): ScaleReading
    val readingFlow: StateFlow<ScaleReading>
    fun tare(): Result<Unit>
    fun zero(): Result<Unit>
}

/**
 * Driver para entornos sin báscula conectada.
 */
class NoneScaleDriver : ScaleDriver {
    private val _readingFlow = MutableStateFlow(ScaleReading("0.000 kg", ScaleStatus.DISCONNECTED))
    override val readingFlow: StateFlow<ScaleReading> = _readingFlow.asStateFlow()

    override fun getAvailablePorts(): List<String> = emptyList()
    override fun connect(config: ScaleConfig): Result<Unit> = Result.success(Unit)
    override fun disconnect() {}
    override fun isConnected(): Boolean = false
    override fun getCurrentReading(): ScaleReading = _readingFlow.value
    override fun tare(): Result<Unit> = Result.success(Unit)
    override fun zero(): Result<Unit> = Result.success(Unit)
}

/**
 * Driver simulador para pruebas de desarrollo, demostración y QA sin hardware físico.
 */
class SimulatedScaleDriver(
    initialWeightKg: Double = 0.0,
    initialStatus: ScaleStatus = ScaleStatus.STABLE
) : ScaleDriver {

    private var currentGrossKg: Double = initialWeightKg
    private var tareKg: Double = 0.0
    private var connected: Boolean = true

    private val _readingFlow = MutableStateFlow(
        computeReading(initialWeightKg, initialStatus)
    )
    override val readingFlow: StateFlow<ScaleReading> = _readingFlow.asStateFlow()

    override fun getAvailablePorts(): List<String> {
        return listOf("SIMULATOR-01", "SIMULATOR-02")
    }

    override fun connect(config: ScaleConfig): Result<Unit> {
        connected = true
        updateFlow()
        return Result.success(Unit)
    }

    override fun disconnect() {
        connected = false
        _readingFlow.value = ScaleReading("0.000 kg", ScaleStatus.DISCONNECTED)
    }

    override fun isConnected(): Boolean = connected

    override fun getCurrentReading(): ScaleReading = _readingFlow.value

    override fun tare(): Result<Unit> {
        if (!connected) return Result.failure(IllegalStateException("Báscula no conectada"))
        tareKg = currentGrossKg
        updateFlow()
        return Result.success(Unit)
    }

    override fun zero(): Result<Unit> {
        if (!connected) return Result.failure(IllegalStateException("Báscula no conectada"))
        currentGrossKg = 0.0
        tareKg = 0.0
        updateFlow()
        return Result.success(Unit)
    }

    fun setSimulatedWeight(grossKg: Double, status: ScaleStatus = ScaleStatus.STABLE) {
        currentGrossKg = grossKg
        _readingFlow.value = computeReading(grossKg - tareKg, status)
    }

    private fun updateFlow() {
        val netKg = currentGrossKg - tareKg
        val status = if (abs(netKg) <= 0.005) ScaleStatus.ZERO else ScaleStatus.STABLE
        _readingFlow.value = computeReading(netKg, status)
    }

    private fun computeReading(netKg: Double, status: ScaleStatus): ScaleReading {
        val clamped = (netKg * 1000.0).toLong() / 1000.0
        val actualStatus = if (abs(clamped) <= 0.005) ScaleStatus.ZERO else status
        return ScaleReading(
            weightText = String.format(Locale.US, "%.3f kg", abs(clamped)),
            status = if (!connected) ScaleStatus.DISCONNECTED else actualStatus
        )
    }
}

/**
 * Driver serial multiplataforma con jSerialComm (soporta Windows COM, Linux /dev/ttyUSB*, macOS /dev/tty.*).
 */
class JSerialCommScaleDriver(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ScaleDriver {

    private var activePort: SerialPort? = null
    private var pollingJob: Job? = null
    private var parser: ScaleProtocolParser = TorreyScaleProtocolParser()

    private val _readingFlow = MutableStateFlow(ScaleReading("0.000 kg", ScaleStatus.DISCONNECTED))
    override val readingFlow: StateFlow<ScaleReading> = _readingFlow.asStateFlow()

    override fun getAvailablePorts(): List<String> {
        return try {
            SerialPort.getCommPorts().map { it.systemPortName }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun connect(config: ScaleConfig): Result<Unit> {
        disconnect()

        if (!config.enabled || config.protocol == ScaleProtocol.NONE) {
            return Result.success(Unit)
        }

        parser = ScaleProtocolParserFactory.create(config.protocol)

        return try {
            val ports = SerialPort.getCommPorts()
            val targetPort = if (config.portName == "AUTO") {
                ports.firstOrNull() ?: return Result.failure(IllegalStateException("No se detectó ningún puerto serial"))
            } else {
                ports.firstOrNull { it.systemPortName.equals(config.portName, ignoreCase = true) }
                    ?: return Result.failure(IllegalStateException("Puerto ${config.portName} no encontrado"))
            }

            targetPort.setBaudRate(config.baudRate)
            targetPort.setNumDataBits(config.dataBits)
            targetPort.setNumStopBits(config.stopBits)
            targetPort.setParity(SerialPort.NO_PARITY)
            targetPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 300, 0)

            if (!targetPort.openPort()) {
                return Result.failure(IllegalStateException("No se pudo abrir el puerto ${targetPort.systemPortName}"))
            }

            activePort = targetPort
            _readingFlow.value = ScaleReading("0.000 kg", ScaleStatus.ZERO)

            startPolling(config)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun startPolling(config: ScaleConfig) {
        pollingJob = scope.launch {
            val port = activePort ?: return@launch
            val reader = BufferedReader(InputStreamReader(port.inputStream))
            val pollCmd = parser.getPollingCommand()

            while (isActive && port.isOpen) {
                try {
                    if (pollCmd != null && port.outputStream != null) {
                        port.outputStream.write(pollCmd)
                        port.outputStream.flush()
                    }

                    if (reader.ready()) {
                        val line = reader.readLine()
                        if (!line.isNullOrBlank()) {
                            val reading = parser.parseRawReading(line)
                            if (reading != null) {
                                _readingFlow.value = reading
                            }
                        }
                    } else if (port.bytesAvailable() > 0) {
                        val buffer = ByteArray(port.bytesAvailable().coerceAtMost(256))
                        val bytesRead = port.readBytes(buffer, buffer.size)
                        if (bytesRead > 0) {
                            val raw = String(buffer, 0, bytesRead)
                            val reading = parser.parseRawReading(raw)
                            if (reading != null) {
                                _readingFlow.value = reading
                            }
                        }
                    }
                } catch (e: Exception) {
                    _readingFlow.value = ScaleReading("0.000 kg", ScaleStatus.DISCONNECTED)
                }
                delay(config.pollIntervalMs.coerceAtLeast(100L))
            }
        }
    }

    override fun disconnect() {
        pollingJob?.cancel()
        pollingJob = null
        activePort?.let {
            if (it.isOpen) {
                try {
                    it.closePort()
                } catch (_: Exception) {}
            }
        }
        activePort = null
        _readingFlow.value = ScaleReading("0.000 kg", ScaleStatus.DISCONNECTED)
    }

    override fun isConnected(): Boolean {
        return activePort?.isOpen == true
    }

    override fun getCurrentReading(): ScaleReading = _readingFlow.value

    override fun tare(): Result<Unit> {
        val port = activePort ?: return Result.failure(IllegalStateException("Báscula no conectada"))
        return try {
            // Comando típico de tara en Torrey y Rhino: 'T' (0x54)
            port.outputStream.write(byteArrayOf('T'.code.toByte()))
            port.outputStream.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun zero(): Result<Unit> {
        val port = activePort ?: return Result.failure(IllegalStateException("Báscula no conectada"))
        return try {
            // Comando típico de cero en Torrey y Rhino: 'Z' (0x5A)
            port.outputStream.write(byteArrayOf('Z'.code.toByte()))
            port.outputStream.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
