package ua.savosh.ecoflowdiag.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Нагадування по замітці: показує сповіщення у призначений час. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        val title = inputData.getString(KEY_TITLE) ?: ""
        if (id >= 0) Notifier.showReminder(applicationContext, id, title)
        return Result.success()
    }

    companion object {
        private const val KEY_ID = "note_id"
        private const val KEY_TITLE = "note_title"

        private fun name(id: Long) = "reminder-$id"

        /** Запланувати нагадування на [whenMs]. Якщо час у минулому — скасувати наявне. */
        fun schedule(ctx: Context, noteId: Long, title: String, whenMs: Long?) {
            val wm = WorkManager.getInstance(ctx)
            if (whenMs == null) {
                wm.cancelUniqueWork(name(noteId))
                return
            }
            val delay = whenMs - System.currentTimeMillis()
            if (delay <= 0) {
                wm.cancelUniqueWork(name(noteId))
                return
            }
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to noteId, KEY_TITLE to title))
                .build()
            wm.enqueueUniqueWork(name(noteId), androidx.work.ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(ctx: Context, noteId: Long) {
            WorkManager.getInstance(ctx).cancelUniqueWork(name(noteId))
        }
    }
}
