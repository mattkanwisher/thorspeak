package nu.hyperworks.thorspeak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nu.hyperworks.thorspeak.data.LookupState
import nu.hyperworks.thorspeak.data.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupSheet(vm: MainViewModel) {
    val state by vm.lookup.collectAsStateWithLifecycle()
    if (state is LookupState.Hidden) return

    ModalBottomSheet(onDismissRequest = { vm.dismissLookup() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val s = state) {
                is LookupState.Loading -> {
                    Text(s.word, style = MaterialTheme.typography.headlineMedium)
                    CircularProgressIndicator()
                }
                is LookupState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is LookupState.Ready -> {
                    val r = s.result
                    Text(r.word, style = MaterialTheme.typography.headlineMedium)
                    Text(r.reading, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text("${r.meaning}  ·  ${r.partOfSpeech}")
                    Text(r.example, style = MaterialTheme.typography.bodyMedium)
                    Text(r.exampleTranslation, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { vm.addToFlashcards(r) },
                        enabled = !s.added,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (s.added) "Added ✓" else "Add to flashcards")
                    }
                }
                LookupState.Hidden -> {}
            }
        }
    }
}
