package nu.hyperworks.thorspeak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nu.hyperworks.thorspeak.ThorSpeakApp
import nu.hyperworks.thorspeak.data.CaptureRegion
import nu.hyperworks.thorspeak.data.MainViewModel
import nu.hyperworks.thorspeak.data.SessionState

/**
 * Drag a rectangle over the latest captured frame; saved as fractional
 * coordinates so letterboxed emulators / HUDs can be excluded. Requires an
 * active capture session to have produced at least one frame.
 */
@Composable
fun RegionSelectScreen(vm: MainViewModel, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val frame by SessionState.lastFrame.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as ThorSpeakApp

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Drag over the dialogue box area", style = MaterialTheme.typography.titleMedium)

        val bmp = frame
        if (bmp == null) {
            Text(
                "No frame captured yet — start a session first, then come back here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStart = offset
                                dragEnd = offset
                            },
                            onDrag = { change, _ ->
                                dragEnd = change.position
                            },
                        )
                    },
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "captured frame",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val a = dragStart
                    val b = dragEnd
                    if (a != null && b != null) {
                        val rect = Rect(
                            Offset(minOf(a.x, b.x), minOf(a.y, b.y)),
                            Offset(maxOf(a.x, b.x), maxOf(a.y, b.y)),
                        )
                        drawRect(
                            Color(0x5500FF00),
                            topLeft = rect.topLeft,
                            size = Size(rect.width, rect.height),
                        )
                        drawRect(
                            Color.Green,
                            topLeft = rect.topLeft,
                            size = Size(rect.width, rect.height),
                            style = Stroke(width = 3f),
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        app.settingsRepository.setRegion(CaptureRegion(0f, 0f, 1f, 1f))
                        onBack()
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("Reset to full screen") }
            Button(
                onClick = {
                    val a = dragStart
                    val b = dragEnd
                    if (a != null && b != null && canvasSize.width > 0 && canvasSize.height > 0 && bmp != null) {
                        // The image is rendered with ContentScale.Fit — map view
                        // coords through the letterboxed image bounds.
                        val viewW = canvasSize.width.toFloat()
                        val viewH = canvasSize.height.toFloat()
                        val scale = minOf(viewW / bmp.width, viewH / bmp.height)
                        val imgW = bmp.width * scale
                        val imgH = bmp.height * scale
                        val offX = (viewW - imgW) / 2f
                        val offY = (viewH - imgH) / 2f
                        fun fx(x: Float) = ((x - offX) / imgW).coerceIn(0f, 1f)
                        fun fy(y: Float) = ((y - offY) / imgH).coerceIn(0f, 1f)
                        val region = CaptureRegion(
                            fx(minOf(a.x, b.x)), fy(minOf(a.y, b.y)),
                            fx(maxOf(a.x, b.x)), fy(maxOf(a.y, b.y)),
                        )
                        scope.launch {
                            app.settingsRepository.setRegion(region)
                            onBack()
                        }
                    }
                },
                enabled = dragStart != null && bmp != null,
                modifier = Modifier.weight(1f),
            ) { Text("Save region") }
        }
    }
}
