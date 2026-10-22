package ua.savosh.ecoflowdiag.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.savosh.ecoflowdiag.data.Prefs
import ua.savosh.ecoflowdiag.net.TelegramConfig
import ua.savosh.ecoflowdiag.net.TelegramSubmit
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Фонова відправка статті у спільну базу (Telegram-бот). Усе — локально-first:
 * стаття вже збережена на телефоні; цей воркер лише надсилає копію й повторює спробу,
 * якщо немає інтернету (жодних помилок користувачу).
 */
class SubmitWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!TelegramConfig.configured()) return Result.success() // відправка ще не налаштована
        val id = inputData.getLong(KEY_ID, -1L)
        if (id < 0) return Result.success()
        val prefs = Prefs(applicationContext)
        val a = prefs.loadArticles().firstOrNull { it.id == id } ?: return Result.success()
        val dir = File(applicationContext.filesDir, "kb_photos")
        val files = a.photos.map { File(dir, it) }
        return try {
            withContext(Dispatchers.IO) {
                TelegramSubmit.sendArticle(a.title, a.section, a.subsection, a.marking, a.analogs, a.body, files)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry() // повтор, коли з'явиться мережа
        }
    }

    companion object {
        private const val KEY_ID = "article_id"

        /** Поставити статтю в чергу на відправку (виконається, щойно буде мережа). */
        fun enqueue(ctx: Context, articleId: Long) {
            val req = OneTimeWorkRequestBuilder<SubmitWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_ID to articleId))
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("submit-$articleId", androidx.work.ExistingWorkPolicy.REPLACE, req)
        }
    }
}
