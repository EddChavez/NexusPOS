package mx.nexuspos.desktop.hardware.scale

import mx.nexuspos.client.features.header.ScaleStatus
import mx.nexuspos.client.features.scale.ScaleProtocol
import kotlin.test.*

class ScaleProtocolParserTest {

    @Test
    fun testTorreyParserFormats() {
        val parser = TorreyScaleProtocolParser()

        // 1. Polling command
        val cmd = parser.getPollingCommand()
        assertNotNull(cmd)
        assertEquals('P'.code.toByte(), cmd[0])

        // 2. Peso cero
        val zeroReading = parser.parseRawReading("   0.000 kg\r")
        assertNotNull(zeroReading)
        assertEquals("0.000 kg", zeroReading.weightText)
        assertEquals(ScaleStatus.ZERO, zeroReading.status)

        // 3. Peso estable (primer lectura = READING, segunda idéntica = STABLE)
        val read1 = parser.parseRawReading("   1.450 kg\r")
        assertNotNull(read1)
        assertEquals("1.450 kg", read1.weightText)

        val read2 = parser.parseRawReading("   1.450 kg\r")
        assertNotNull(read2)
        assertEquals("1.450 kg", read2.weightText)
        assertEquals(ScaleStatus.STABLE, read2.status)

        // 4. Peso inestable con bandera US
        val unstable = parser.parseRawReading("US,GS,+   2.100kg\r\n")
        assertNotNull(unstable)
        assertEquals("2.100 kg", unstable.weightText)
        assertEquals(ScaleStatus.READING, unstable.status)
    }

    @Test
    fun testRhinoParserFormats() {
        val parser = RhinoScaleProtocolParser()

        // 1. Peso cero
        val zero = parser.parseRawReading("WN   0.000 kg\r\n")
        assertNotNull(zero)
        assertEquals("0.000 kg", zero.weightText)
        assertEquals(ScaleStatus.ZERO, zero.status)

        // 2. Peso estable
        val r1 = parser.parseRawReading("WN   3.200 kg\r\n")
        assertNotNull(r1)
        assertEquals("3.200 kg", r1.weightText)

        val r2 = parser.parseRawReading("WN   3.200 kg\r\n")
        assertNotNull(r2)
        assertEquals("3.200 kg", r2.weightText)
        assertEquals(ScaleStatus.STABLE, r2.status)

        // 3. Inestable
        val un = parser.parseRawReading("US   3.200 kg\r\n")
        assertNotNull(un)
        assertEquals(ScaleStatus.READING, un.status)
    }

    @Test
    fun testGenericParserUnitConversions() {
        val parser = GenericScaleProtocolParser()

        // 1. Gramos a Kilogramos
        val grams = parser.parseRawReading("500 g\r\n")
        assertNotNull(grams)
        assertEquals("0.500 kg", grams.weightText)

        // 2. Decimal directo en KG
        val kg = parser.parseRawReading("1.750 kg")
        assertNotNull(kg)
        assertEquals("1.750 kg", kg.weightText)

        // 3. Factory
        val torrey = ScaleProtocolParserFactory.create(ScaleProtocol.TORREY)
        assertTrue(torrey is TorreyScaleProtocolParser)

        val rhino = ScaleProtocolParserFactory.create(ScaleProtocol.RHINO)
        assertTrue(rhino is RhinoScaleProtocolParser)
    }
}
