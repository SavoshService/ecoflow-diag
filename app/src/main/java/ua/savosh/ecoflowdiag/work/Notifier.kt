package ua.savosh.ecoflowdiag.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import ua.savosh.ecoflowdiag.net.UpdateInfo
import ua.savosh.ecoflowdiag.ui.MainActivity

object Notifier {
    private const val CHANNEL = "updates"
    private const val NOTIF_ID = 4201

    fun showUpdate(ctx: Context, upd: UpdateInfo) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Оновлення застосунку", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        // Відкриваємо застосунок і одразу запускаємо завантаження+встановлення оновлення.
        val open = Intent(ctx, MainActivity::class.java).apply {
            putExtra("update_url", upd.url)
            putExtra("update_version", upd.version)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(ctx, 0, open, flags)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle("Доступна версія ${upd.version}")
            .setContentText(if (upd.notes.isNotBlank()) upd.notes else "Торкніться, щоб оновити")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    private const val REMINDER_CHANNEL = "reminders"

    /** Сповіщення-нагадування по замітці. */
    fun showReminder(ctx: Context, noteId: Long, title: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(REMINDER_CHANNEL, "Нагадування", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val open = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(ctx, noteId.toInt(), open, flags)
        val n = NotificationCompat.Builder(ctx, REMINDER_CHANNEL)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle("🔔 Нагадування")
            .setContentText(title.ifBlank { "Замітка" })
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(5200 + noteId.toInt(), n)
    }
}
