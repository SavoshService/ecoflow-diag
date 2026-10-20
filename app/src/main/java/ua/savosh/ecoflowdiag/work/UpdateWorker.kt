package ua.savosh.ecoflowdiag.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.savosh.ecoflowdiag.data.Prefs
import ua.savosh.ecoflowdiag.net.Updater

/** Фонова перевірка оновлень: якщо є новіша версія і про неї ще не сповіщали — показати сповіщення. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val upd = withContext(Dispatchers.IO) { Updater.check() } ?: return Result.success()
        val prefs = Prefs(applicationContext)
        if (prefs.lastNotifiedVersion == upd.version) return Result.success()
        Notifier.showUpdate(applicationContext, upd)
        prefs.lastNotifiedVersion = upd.version
        return Result.success()
    }
}
