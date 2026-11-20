package mx.nexuspos.desktop.hardware

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

object WhatsAppDispatcher {

    /**
     * Opens the WhatsApp web / app link using default system browser or desktop application.
     */
    fun openWhatsApp(url: String): Result<Unit> {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
                Result.success(Unit)
            } else {
                // Fallback for macOS runtime or headless
                val runtime = Runtime.getRuntime()
                val os = System.getProperty("os.name").lowercase()
                when {
                    os.contains("mac") -> runtime.exec(arrayOf("open", url))
                    os.contains("win") -> runtime.exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", url))
                    else -> runtime.exec(arrayOf("xdg-open", url))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copies text to system clipboard.
     */
    fun copyToClipboard(text: String): Result<Unit> {
        return try {
            val selection = StringSelection(text)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(selection, selection)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
