package nu.hyperworks.thorspeak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nu.hyperworks.thorspeak.BuildConfig
import nu.hyperworks.thorspeak.ThorSpeakApp
import nu.hyperworks.thorspeak.data.MainViewModel
import nu.hyperworks.thorspeak.net.UpdateManager

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onSelectRegion: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    val scope = rememberCoroutineScope()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as ThorSpeakApp
    val repo = app.settingsRepository

    var url by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var interval by remember { mutableStateOf(s.intervalMs.toFloat()) }
    var cacheMb by remember { mutableStateOf(s.cacheMaxMb.toFloat()) }
    var voiceJa by remember(s.voiceJa) { mutableStateOf(s.voiceJa) }
    var voiceEn by remember(s.voiceEn) { mutableStateOf(s.voiceEn) }
    var voiceTh by remember(s.voiceTh) { mutableStateOf(s.voiceTh) }

    LaunchedEffect(s.intervalMs) { interval = s.intervalMs.toFloat() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                scope.launch {
                    repo.setServerUrl(url)
                    vm.testServer { testResult = it }
                }
            }) { Text("Test") }
        }
        testResult?.let { Text(it, style = MaterialTheme.typography.labelMedium) }

        Text("Capture interval: ${(interval / 1000f).let { "%.1f".format(it) }} s")
        Slider(
            value = interval,
            onValueChange = { interval = it },
            onValueChangeFinished = { scope.launch { repo.setIntervalMs(interval.toLong()) } },
            valueRange = 500f..5000f,
        )

        Text("Audio cache limit: ${cacheMb.toInt()} MB")
        Slider(
            value = cacheMb,
            onValueChange = { cacheMb = it },
            onValueChangeFinished = { scope.launch { repo.setCacheMaxMb(cacheMb.toInt()) } },
            valueRange = 50f..1000f,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Translation overlay")
                Text(
                    "Float the translation over the game text on the top screen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val overlayCtx = androidx.compose.ui.platform.LocalContext.current
            Switch(
                checked = s.overlayEnabled,
                onCheckedChange = { v ->
                    if (v && !AndroidSettings.canDrawOverlays(overlayCtx)) {
                        overlayCtx.startActivity(
                            Intent(
                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${overlayCtx.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    scope.launch { repo.setOverlayEnabled(v) }
                },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pixel-only gate")
                Text(
                    "Skip on-device OCR; send every stable changed frame",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = s.pixelOnlyGate,
                onCheckedChange = { v -> scope.launch { repo.setPixelOnlyGate(v) } },
            )
        }

        OutlinedButton(onClick = onSelectRegion, modifier = Modifier.fillMaxWidth()) {
            Text(if (s.region.isFullFrame()) "Select capture region (full screen)" else "Capture region: custom")
        }

        Text("Voices (blank = server default)", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(value = voiceJa, onValueChange = { voiceJa = it },
            label = { Text("Japanese voice, e.g. ja-JP-KeitaNeural") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = voiceEn, onValueChange = { voiceEn = it },
            label = { Text("English voice, e.g. en-US-GuyNeural") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = voiceTh, onValueChange = { voiceTh = it },
            label = { Text("Thai voice, e.g. th-TH-NiwatNeural") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            scope.launch {
                repo.setVoice("ja", voiceJa.trim())
                repo.setVoice("en", voiceEn.trim())
                repo.setVoice("th", voiceTh.trim())
            }
        }) { Text("Save voices") }

        Text(
            "On-device audio cache: %.1f MB used".format(app.audioCache.sizeBytes() / 1048576f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("App updates", style = MaterialTheme.typography.titleSmall)
        val context = androidx.compose.ui.platform.LocalContext.current
        val updater = remember { UpdateManager(app.apiClient.http) }
        var updateStatus by remember { mutableStateOf<String?>(null) }
        var updating by remember { mutableStateOf(false) }
        OutlinedButton(
            enabled = !updating,
            onClick = {
                updating = true
                updateStatus = "Checking GitHub…"
                scope.launch(Dispatchers.IO) {
                    try {
                        val release = updater.latestRelease()
                        if (!updater.isNewer(release.tag, BuildConfig.VERSION_NAME)) {
                            updateStatus = "Up to date (v${BuildConfig.VERSION_NAME})"
                        } else {
                            updateStatus = "Downloading ${release.tag}… 0%"
                            val apk = updater.downloadApk(release, context) { pct ->
                                updateStatus = "Downloading ${release.tag}… $pct%"
                            }
                            updateStatus = "Installing ${release.tag} — confirm the system prompt"
                            updater.install(context, apk)
                        }
                    } catch (e: Exception) {
                        updateStatus = "Update failed: ${e.message}"
                    } finally {
                        updating = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Check for updates (installed: v${BuildConfig.VERSION_NAME})") }
        updateStatus?.let {
            Text(it, style = MaterialTheme.typography.labelMedium)
        }
    }
}
