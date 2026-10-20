package ua.savosh.ecoflowdiag.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import ua.savosh.ecoflowdiag.work.UpdateScheduler

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Дозвіл на сповіщення (Android 13+) — щоб повідомляти про нові версії
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        // Фонова перевірка оновлень раз на ~6 год
        UpdateScheduler.schedule(this)

        setContent {
            SavoshTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App(vm)
                }
            }
        }
        handleUpdateIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdateIntent(intent)
    }

    /** Перехід зі сповіщення про оновлення: одразу качаємо APK і відкриваємо інсталятор. */
    private fun handleUpdateIntent(intent: Intent?) {
        val url = intent?.getStringExtra("update_url") ?: return
        val version = intent.getStringExtra("update_version") ?: ""
        vm.startUpdate(this, url, version)
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
        Screen.PARTS -> PartsScreen(vm)
        Screen.STATIONINFO -> StationInfoScreen(vm)
    }
}
