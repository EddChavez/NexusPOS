package mx.nexuspos.desktop.hardware.scale

import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.scale.ScaleConfig
import kotlin.test.*

class SimulatedScaleDriverTest {

    @Test
    fun testSimulatedDriverLifecycleAndTare() {
        val driver = SimulatedScaleDriver(initialWeightKg = 0.0)

        // Estado inicial en cero
        val initial = driver.getCurrentReading()
        assertEquals("0.000 kg", initial.weightText)
        assertEquals(ScaleStatus.ZERO, initial.status)

        // Simular producto colocado de 1.500 kg
        driver.setSimulatedWeight(1.500, ScaleStatus.STABLE)
        val withWeight = driver.getCurrentReading()
        assertEquals("1.500 kg", withWeight.weightText)
        assertEquals(ScaleStatus.STABLE, withWeight.status)
        assertEquals(1.500, withWeight.weightKg)
        assertTrue(withWeight.isStableWeight)

        // Ejecutar TARA (debe volver a 0.000 kg neto)
        val tareRes = driver.tare()
        assertTrue(tareRes.isSuccess)

        val tared = driver.getCurrentReading()
        assertEquals("0.000 kg", tared.weightText)
        assertEquals(ScaleStatus.ZERO, tared.status)

        // Poner a CERO (reset total)
        val zeroRes = driver.zero()
        assertTrue(zeroRes.isSuccess)

        // Desconectar
        driver.disconnect()
        assertFalse(driver.isConnected())
        assertEquals(ScaleStatus.DISCONNECTED, driver.getCurrentReading().status)
    }
}
