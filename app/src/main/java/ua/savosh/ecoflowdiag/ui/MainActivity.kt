package ua.savosh.ecoflowdiag.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SavoshTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppForeground()
    }

    override fun onStop() {
        vm.onAppBackground()
        super.onStop()
    }
}

@Composable
private fun App(vm: AppViewModel) {
    BackHandler(enabled = vm.screen != Screen.LOGIN && vm.screen != Screen.STATIONS) { vm.back() }
    when (vm.screen) {
        Screen.LOGIN -> LoginScreen(vm)
        Screen.STATIONS -> StationsScreen(vm)
        Screen.DIAG -> DiagScreen(vm)
        Screen.RAW -> RawScreen(vm)
        Screen.LOG -> LogScreen(vm)
        Screen.FLASH -> FlashScreen(vm)
        Screen.CAPTEST -> CapTestScreen(vm)
        Screen.CONTROL -> ControlScreen(vm)
        Screen.FEEDBACK -> FeedbackScreen(vm)
    }
}
