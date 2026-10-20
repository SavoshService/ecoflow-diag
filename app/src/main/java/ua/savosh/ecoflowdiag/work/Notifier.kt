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
}
