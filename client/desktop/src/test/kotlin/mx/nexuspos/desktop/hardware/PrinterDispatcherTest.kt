package mx.nexuspos.desktop.hardware

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.settings.PrinterUiConfig
import mx.nexuspos.client.features.settings.SettingsTab
import mx.nexuspos.client.features.settings.SettingsViewModel
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.sales.SaleLineItemResponse
import mx.nexuspos.contracts.sales.SalePaymentResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class PrinterDispatcherTest {

    private val dispatcher: PrinterDispatcher = DefaultPrinterDispatcher()

    @Test
    fun testPaperWidthColumnsAndLabels() {
        assertEquals(42, PaperWidth.MM_80.columns)
        assertEquals(32, PaperWidth.MM_58.columns)
        assertEquals(PaperWidth.MM_58, PaperWidth.fromString("58mm"))
        assertEquals(PaperWidth.MM_58, PaperWidth.fromString("58"))
        assertEquals(PaperWidth.MM_80, PaperWidth.fromString("80mm"))
        assertEquals(PaperWidth.MM_80, PaperWidth.fromString("invalid"))
        assertEquals(PaperWidth.MM_58, PaperWidth.fromColumns(32))
        assertEquals(PaperWidth.MM_80, PaperWidth.fromColumns(42))
    }

    @Test
    fun testDrawerKickBytesGeneration() {
        val printer = EscPosReceiptPrinter(PaperWidth.MM_80)
        val bytes = printer.generateDrawerKickBytes()

        assertTrue(bytes.isNotEmpty())
        // Should contain ESC p (0x1B, 0x70)
        assertTrue(bytes.any { it == 0x1B.toByte() })
        assertTrue(bytes.any { it == 0x70.toByte() })
    }

    @Test
    fun testTestReceiptGeneration() {
        val printer = EscPosReceiptPrinter(PaperWidth.MM_80)
        val config = PrinterConfig(
            connectionType = PrinterConnectionType.ETHERNET_TCP,
            tcpHost = "192.168.1.150",
            tcpPort = 9100,
            paperWidth = PaperWidth.MM_80
        )
        val text = printer.generateTestReceiptText("Mi Super Abarrotes", config)

        assertTrue(text.contains("Mi Super Abarrotes"))
        assertTrue(text.contains("PRUEBA DE IMPRESIÓN"))
        assertTrue(text.contains("ETHERNET_TCP"))
        assertTrue(text.contains("192.168.1.150:9100"))
        assertTrue(text.contains("COMUNICACIÓN ESC/POS EXITOSA"))

        val bytes = printer.generateTestReceiptBytes("Mi Super Abarrotes", config)
        assertTrue(bytes.size > 50)
    }

    @Test
    fun testPrinterConfigRepositoryPersistence() {
        val tempFile = File.createTempFile("test_printer_config", ".properties")
        try {
            val repo = PrinterConfigRepository(tempFile)

            val initialConfig = repo.load()
            assertEquals(PrinterConnectionType.NONE, initialConfig.connectionType)

            val customConfig = PrinterConfig(
                connectionType = PrinterConnectionType.ETHERNET_TCP,
                osPrinterName = "EPSON_TM_T20",
                tcpHost = "192.168.1.188",
                tcpPort = 9100,
                paperWidth = PaperWidth.MM_58,
                autoPrintOnSale = false,
                openDrawerOnCashSale = true,
                connectionTimeoutMs = 2500
            )

            repo.save(customConfig)

            val loadedConfig = repo.load()
            assertEquals(PrinterConnectionType.ETHERNET_TCP, loadedConfig.connectionType)
            assertEquals("EPSON_TM_T20", loadedConfig.osPrinterName)
            assertEquals("192.168.1.188", loadedConfig.tcpHost)
            assertEquals(9100, loadedConfig.tcpPort)
            assertEquals(PaperWidth.MM_58, loadedConfig.paperWidth)
            assertFalse(loadedConfig.autoPrintOnSale)
            assertTrue(loadedConfig.openDrawerOnCashSale)
            assertEquals(2500, loadedConfig.connectionTimeoutMs)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testPrinterDispatcherNoneModeIsNoOp() {
        val noneConfig = PrinterConfig(connectionType = PrinterConnectionType.NONE)
        val dummySale = createDummySale()

        val printRes = dispatcher.printSaleReceipt(dummySale, null, noneConfig)
        assertTrue(printRes.isSuccess)

        val drawerRes = dispatcher.openCashDrawer(noneConfig)
        assertTrue(drawerRes.isSuccess)

        val testRes = dispatcher.printTestReceipt("Tienda", noneConfig)
        assertTrue(testRes.isFailure, "Probar impresión con NONE debe fallar con mensaje claro")
    }

    @Test
    fun testPrinterDispatcherTcpSocketTransmission() {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val receivedBytes = ByteArrayOutputStream()
        val serverReadyLatch = CountDownLatch(1)
        val transmissionDoneLatch = CountDownLatch(1)

        val serverThread = Thread {
            serverReadyLatch.countDown()
            try {
                serverSocket.soTimeout = 4000
                val clientSocket = serverSocket.accept()
                clientSocket.getInputStream().use { input ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        receivedBytes.write(buffer, 0, read)
                    }
                }
                clientSocket.close()
            } catch (e: Exception) {
                // Socket closed or timeout
            } finally {
                transmissionDoneLatch.countDown()
                serverSocket.close()
            }
        }
        serverThread.start()

        assertTrue(serverReadyLatch.await(2, TimeUnit.SECONDS))

        val tcpConfig = PrinterConfig(
            connectionType = PrinterConnectionType.ETHERNET_TCP,
            tcpHost = "127.0.0.1",
            tcpPort = port,
            connectionTimeoutMs = 2000
        )

        val dummySale = createDummySale()
        val res = dispatcher.printSaleReceipt(dummySale, SettingsDto(storeName = "Tienda TCP Test"), tcpConfig)
        assertTrue(res.isSuccess, "El envío por Socket TCP al puerto local debió ser exitoso: ${res.exceptionOrNull()?.message}")

        assertTrue(transmissionDoneLatch.await(3, TimeUnit.SECONDS))

        val receivedData = receivedBytes.toByteArray()
        assertTrue(receivedData.isNotEmpty(), "La impresora TCP simulada debió recibir los bytes")
        val receivedString = String(receivedData, Charsets.ISO_8859_1)
        assertTrue(receivedString.contains("Tienda TCP Test") || receivedString.contains("Folio: #501"))
    }

    @Test
    fun testPrinterDispatcherTcpSocketFailureHandledGracefully() {
        val unusedPort = 59999
        val config = PrinterConfig(
            connectionType = PrinterConnectionType.ETHERNET_TCP,
            tcpHost = "127.0.0.1",
            tcpPort = unusedPort,
            connectionTimeoutMs = 300
        )

        val res = dispatcher.printBytes(byteArrayOf(0x1B, 0x40), config)
        assertTrue(res.isFailure, "Conectar a un puerto cerrado debe retornar Result.failure")
        val message = res.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("127.0.0.1") || message.contains("TCP"))
    }

    @Test
    fun testSettingsViewModelPrinterConfigurationFlow() {
        val fakeApiClient = object : ApiClient(baseUrl = "http://localhost:8080") {
            override suspend fun getSettings(): Result<SettingsDto> = Result.success(SettingsDto())
        }
        val vm = SettingsViewModel(fakeApiClient, CoroutineScope(Dispatchers.Unconfined))

        var savedConfig: PrinterUiConfig? = null
        var testPrintInvoked = false
        var testDrawerInvoked = false

        vm.printerConfigLoader = {
            PrinterUiConfig(
                connectionType = "USB_OS",
                osPrinterName = "Zebra_POS",
                paperWidth = "80mm"
            )
        }
        vm.printerConfigSaver = { savedConfig = it }
        vm.osPrintersProvider = { listOf("Zebra_POS", "EPSON_TM_T20") }
        vm.testPrintAction = {
            testPrintInvoked = true
            Result.success(Unit)
        }
        vm.testDrawerAction = {
            testDrawerInvoked = true
            Result.success(Unit)
        }

        vm.loadPrinterConfig()
        val loadedState = vm.uiState.value
        assertEquals("USB_OS", loadedState.printerConnectionType)
        assertEquals("Zebra_POS", loadedState.osPrinterName)
        assertEquals(2, loadedState.availableOsPrinters.size)

        vm.selectTab(SettingsTab.PRINTER)
        assertEquals(SettingsTab.PRINTER, vm.uiState.value.selectedTab)

        // Modify fields
        vm.onPrinterConnectionTypeChanged("ETHERNET_TCP")
        vm.onTcpHostChanged("192.168.1.99")
        vm.onTcpPortChanged("9200")
        vm.onPaperWidthChanged("58mm")
        vm.onAutoPrintOnSaleChanged(false)

        vm.savePrinterConfig()
        assertNotNull(savedConfig)
        assertEquals("ETHERNET_TCP", savedConfig.connectionType)
        assertEquals("192.168.1.99", savedConfig.tcpHost)
        assertEquals(9200, savedConfig.tcpPort)
        assertEquals("58mm", savedConfig.paperWidth)
        assertFalse(savedConfig.autoPrintOnSale)
        assertNotNull(vm.uiState.value.successBanner)

        vm.testPrinter()
        assertTrue(testPrintInvoked)

        vm.testCashDrawer()
        assertTrue(testDrawerInvoked)
    }

    private fun createDummySale(): SaleResponse {
        return SaleResponse(
            id = "sale-501",
            folio = 501,
            cashSessionId = "sess-1",
            cashierName = "Cajero Test",
            customerName = null,
            status = "COMPLETED",
            subtotalCents = 2000L,
            taxCents = 0L,
            totalCents = 2000L,
            lines = listOf(
                SaleLineItemResponse(
                    id = "line-1",
                    productId = "p-1",
                    presentationId = "pres-1",
                    description = "Refresco Cola",
                    quantityMillis = 1000L,
                    unitPriceCents = 2000L,
                    taxRate = 0.0,
                    taxCents = 0L,
                    totalCents = 2000L
                )
            ),
            payments = listOf(
                SalePaymentResponse("pay-1", "CASH", 2000L, 2000L, 0L)
            ),
            createdAt = 1713139200000L
        )
    }
}
