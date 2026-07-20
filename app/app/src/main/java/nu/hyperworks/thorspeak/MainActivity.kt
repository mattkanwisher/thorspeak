package nu.hyperworks.thorspeak

import android.Manifest
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nu.hyperworks.thorspeak.capture.CaptureService
import nu.hyperworks.thorspeak.data.MainViewModel
import nu.hyperworks.thorspeak.ui.FlashcardsScreen
import nu.hyperworks.thorspeak.ui.LookupSheet
import nu.hyperworks.thorspeak.ui.MainScreen
import nu.hyperworks.thorspeak.ui.RegionSelectScreen
import nu.hyperworks.thorspeak.ui.SettingsScreen

enum class Screen { Main, Flashcards, Settings, RegionSelect }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ThorSpeakRoot()
            }
        }
    }

    @Composable
    private fun ThorSpeakRoot() {
        val vm: MainViewModel = viewModel()
        var screen by remember { mutableStateOf(Screen.Main) }
        val snackbar = remember { SnackbarHostState() }

        val message by vm.message.collectAsStateWithLifecycle()
        LaunchedEffect(message) {
            message?.let {
                snackbar.showSnackbar(it)
                vm.clearMessage()
            }
        }

        val projectionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                CaptureService.start(this, result.resultCode, data)
            }
        }

        val notificationLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _ ->
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }

        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            val modifier = Modifier.padding(padding)
            when (screen) {
                Screen.Main -> MainScreen(
                    vm = vm,
                    modifier = modifier,
                    onStart = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onStop = { CaptureService.stop(this) },
                    onNavigate = { screen = it },
                )
                Screen.Flashcards -> FlashcardsScreen(vm, modifier) { screen = Screen.Main }
                Screen.Settings -> SettingsScreen(vm, modifier, onSelectRegion = { screen = Screen.RegionSelect }) {
                    screen = Screen.Main
                }
                Screen.RegionSelect -> RegionSelectScreen(vm, modifier) { screen = Screen.Settings }
            }
            LookupSheet(vm)
        }
    }
}
