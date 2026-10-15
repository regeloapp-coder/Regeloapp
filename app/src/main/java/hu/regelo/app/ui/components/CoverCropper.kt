package hu.regelo.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hu.regelo.app.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Képkivágó adott képarányra (alapból 2:3 borító). Húzással mozgatható, csippentéssel nagyítható.
 * Az eredmény [outW]×[outH] képpontos JPEG-bájttömb.
 */
@Composable
fun ImageCropDialog(
    bitmap: Bitmap,
    aspect: Float = 2f / 3f,
    outW: Int = 800,
    outH: Int = 1200,
    title: String = "Borító kivágása (2:3)",
    onDismiss: () -> Unit,
    onCropped: (ByteArray) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var frameW by remember { mutableFloatStateOf(1f) }
    var frameH by remember { mutableFloatStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val iw = bitmap.width.toFloat()
    val ih = bitmap.height.toFloat()

    fun clampOffsets() {
        val base = max(frameW / iw, frameH / ih)
        val maxX = max(0f, (iw * base * scale - frameW) / 2f)
        val maxY = max(0f, (ih * base * scale - frameH) / 2f)
        dx = dx.coerceIn(-maxX, maxX)
        dy = dy.coerceIn(-maxY, maxY)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("Húzd és csippentsd a képet, hogy a keretbe kerüljön, amit látni szeretnél.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .aspectRatio(aspect, matchHeightConstraintsFirst = maxHeight < maxWidth / aspect)
                            .clipToBounds()
                            .background(Color.Black)
                            .border(2.dp, MaterialTheme.colorScheme.secondary)
                            .onSizeChanged { frameW = it.width.toFloat(); frameH = it.height.toFloat(); clampOffsets() }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    dx += pan.x
                                    dy += pan.y
                                    clampOffsets()
                                }
                            },
                    ) {
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationX = dx; translationY = dy
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Mégse") }
                    Button(enabled = !busy, onClick = {
                        busy = true
                        val base = max(frameW / iw, frameH / ih)
                        val s = base * scale
                        val dispW = iw * s
                        val dispH = ih * s
                        val left = ((dispW - frameW) / 2f - dx) / s
                        val top = ((dispH - frameH) / 2f - dy) / s
                        val w = frameW / s
                        val h = frameH / s
                        // A kivágás és a JPEG-tömörítés nagy képnél lassú: háttérszálon fut, hogy a felület ne akadjon.
                        scope.launch {
                            val bytes = try {
                                withContext(Dispatchers.Default) {
                                    val cropped = Images.cropNormalized(
                                        bitmap,
                                        Images.clamp01(left / iw), Images.clamp01(top / ih),
                                        min(1f, w / iw), min(1f, h / ih),
                                        outW, outH,
                                    )
                                    Images.toJpeg(cropped, 86)
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                hu.regelo.app.util.ErrorReporter.report(e, "image-crop")
                                null
                            }
                            busy = false
                            if (bytes != null) onCropped(bytes) else onDismiss()
                        }
                    }) { Text(if (busy) "Feldolgozás…" else "Kivágás és mentés") }
                }
            }
        }
    }
}
