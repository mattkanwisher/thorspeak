package nu.hyperworks.thorspeak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nu.hyperworks.thorspeak.Screen
import nu.hyperworks.thorspeak.data.MainViewModel
import nu.hyperworks.thorspeak.data.SessionState
import nu.hyperworks.thorspeak.data.SessionStatus

private val LANGS = listOf("ja" to "日本語", "en" to "English", "th" to "ไทย")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val running by SessionState.running.collectAsStateWithLifecycle()
    val status by SessionState.status.collectAsStateWithLifecycle()
    val response by SessionState.lastResponse.collectAsStateWithLifecycle()
    val gateLog by SessionState.gateLog.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = null)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (running) onStop() else onStart() },
                colors = if (running) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (running) "Stop" else "Start session")
            }
            OutlinedButton(onClick = { vm.replay() }) { Text("Replay") }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LANGS.forEachIndexed { index, (code, label) ->
                SegmentedButton(
                    selected = settings?.lang == code,
                    onClick = { vm.setLang(code) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = LANGS.size),
                ) { Text(label) }
            }
        }

        val statusText = when (val s = status) {
            SessionStatus.Idle -> "Idle"
            SessionStatus.Capturing -> "Watching top screen…"
            SessionStatus.Processing -> "Reading text…"
            SessionStatus.Speaking -> "Speaking"
            is SessionStatus.Error -> s.message
        }
        Text(
            "$statusText  ·  $gateLog",
            style = MaterialTheme.typography.labelSmall,
            color = if (status is SessionStatus.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            val text = response?.text.orEmpty()
            if (text.isBlank()) {
                Text("Recognized dialogue appears here. Tap a word to look it up.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Tokenize.split(text).forEach { token ->
                        if (token.lookupable) {
                            TextButton(
                                onClick = { vm.lookupWord(token.text) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                            ) {
                                Text(token.text, style = MaterialTheme.typography.titleLarge)
                            }
                        } else {
                            Text(token.text, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                response?.translation?.let { translation ->
                    Spacer(Modifier.height(8.dp))
                    Text(translation, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { vm.loadFlashcards(); onNavigate(Screen.Flashcards) }, modifier = Modifier.weight(1f)) {
                Text("Flashcards")
            }
            OutlinedButton(onClick = { onNavigate(Screen.Settings) }, modifier = Modifier.weight(1f)) {
                Text("Settings")
            }
        }
    }
}
