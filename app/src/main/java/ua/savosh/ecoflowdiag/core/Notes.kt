package ua.savosh.ecoflowdiag.core

/** Пункт чеклиста. */
data class ChecklistItem(val text: String, val done: Boolean = false)

/**
 * Замітка записника.
 * kind: "plain" — текст; "checklist" — список з галочками; "station" — прив'язана до станції (stationSn).
 */
data class Note(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val kind: String = "plain",
    val title: String = "",
    val body: String = "",
    val items: List<ChecklistItem> = emptyList(),
    val stationSn: String = "",
    val reminderAt: Long? = null,
)

object Notes {
    const val PLAIN = "plain"
    const val CHECKLIST = "checklist"
    const val STATION = "station"

    fun kindLabel(kind: String): String = when (kind) {
        CHECKLIST -> "Чеклист"
        STATION -> "До станції"
        else -> "Замітка"
    }

    fun kindEmoji(kind: String): String = when (kind) {
        CHECKLIST -> "☑"
        STATION -> "🔋"
        else -> "📝"
    }

    /** Прогрес чеклиста, напр. "2/5", або порожньо для інших типів. */
    fun progress(n: Note): String =
        if (n.kind == CHECKLIST && n.items.isNotEmpty()) "${n.items.count { it.done }}/${n.items.size}" else ""
}
