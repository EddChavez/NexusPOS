package org.ozafry.nexuspos

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun BarcodeScanner(owner: LifecycleOwner, onClose: () -> Unit, onScan: (String) -> Unit) {
    val context = LocalContext.current
    val currentScan by rememberUpdatedState(onScan)
    val preview = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var problem by remember { mutableStateOf(false) }

    DisposableEffect(owner, preview) {
        val worker = Executors.newSingleThreadExecutor()
        val active = AtomicBoolean(true)
        val accepted = AtomicBoolean(false)
        val main = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val viewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val reader = MultiFormatReader()
        var provider: ProcessCameraProvider? = null
        var lastFrame = 0L

        analysis.setAnalyzer(worker) { frame ->
            try {
                val now = android.os.SystemClock.elapsedRealtime()
                if (active.get() && !accepted.get() && now - lastFrame >= 150) {
                    lastFrame = now
                    decode(frame, reader)?.takeIf { it.isNotBlank() }?.let { code ->
                        if (accepted.compareAndSet(false, true)) main.execute { if (active.get()) currentScan(code) }
                    }
                }
            } finally {
                frame.close()
            }
        }
        providerFuture.addListener({
            if (active.get()) {
                try {
                    val camera = providerFuture.get()
                    provider = camera
                    val selector = if (camera.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA
                        else CameraSelector.DEFAULT_FRONT_CAMERA
                    camera.bindToLifecycle(owner, selector, viewUseCase, analysis)
                } catch (_: Exception) {
                    problem = true
                }
            }
        }, main)
        onDispose {
            active.set(false)
            analysis.clearAnalyzer()
            provider?.unbind(viewUseCase, analysis)
            worker.shutdown()
        }
    }

    Dialog(onDismissRequest = onClose) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Escanear código", style = MaterialTheme.typography.titleLarge)
                if (problem) Text("La cámara no está disponible. Captura el código manualmente.")
                else AndroidView({ preview }, Modifier.fillMaxWidth().height(280.dp))
                TextButton(onClick = onClose) { Text("Capturar manualmente") }
            }
        }
    }
}

private fun decode(frame: ImageProxy, reader: MultiFormatReader): String? {
    val width = frame.width
    val height = frame.height
    val plane = frame.planes.first()
    val buffer = plane.buffer
    val bytes = ByteArray(width * height)
    for (row in 0 until height) {
        val offset = row * plane.rowStride
        if (plane.pixelStride == 1) {
            buffer.position(offset)
            buffer.get(bytes, row * width, width)
        } else {
            for (column in 0 until width) bytes[row * width + column] = buffer.get(offset + column * plane.pixelStride)
        }
    }
    fun read(luma: ByteArray, w: Int, h: Int): String? = try {
        val source = PlanarYUVLuminanceSource(luma, w, h, 0, 0, w, h, false)
        reader.decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }
    read(bytes, width, height)?.let { return it }
    val rotated = ByteArray(bytes.size)
    for (row in 0 until height) for (column in 0 until width) {
        rotated[column * height + (height - 1 - row)] = bytes[row * width + column]
    }
    return read(rotated, height, width)
}
