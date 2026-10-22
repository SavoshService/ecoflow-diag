package ua.savosh.ecoflowdiag.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Посилання на банку для підтримки автора (Monobank: https://send.monobank.ua/jar/…).
 * Порожній рядок — кнопки «Підтримати автора» в програмі не буде.
 */
const val DONATE_URL = "https://send.monobank.ua/jar/6PSGdbemwh"

const val DONATE_TEXT = "☕ Підтримати автора"

/** Телеграм-канал Savosh Service — для зворотного зв'язку й питань. */
const val TELEGRAM_URL = "https://t.me/savosh_service"

/** Група спільноти майстрів (кнопка «Канал спільноти» в розділі Деталі) — посилання-запрошення. */
const val COMMUNITY_URL = "https://t.me/+kQgBAgWXnU83MjEy"

fun donateEnabled(): Boolean = DONATE_URL.startsWith("https://")

/** Відкриває будь-яке посилання у браузері / відповідному застосунку. */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        shareText(context, url, "Посилання")
    }
}

fun openTelegram(context: Context) = openUrl(context, TELEGRAM_URL)

/** Відкриває банку: у застосунку банку, якщо він установлений, інакше в браузері. */
fun openDonate(context: Context) {
    if (!donateEnabled()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL)))
    } catch (_: ActivityNotFoundException) {
        shareText(context, DONATE_URL, "Підтримати автора")
    }
}
