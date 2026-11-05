package ua.savosh.ecoflowdiag.core

import java.util.Calendar

/** Статус замовлення майстерні. */
enum class OrderStatus(val key: String, private val labelKey: String, private val hintKey: String) {
    RED("red", tk("В черзі"), tk("Щойно прийнятий")),
    YELLOW("yellow", tk("В роботі"), tk("Діагностика / чекає деталі")),
    GREEN("green", tk("Готово"), tk("Ремонт завершено")),
    ;

    val label: String get() = tr(labelKey)
    val hint: String get() = tr(hintKey)

    companion object {
        fun of(k: String?): OrderStatus = entries.firstOrNull { it.key == k } ?: RED
    }
}

/** Канал, яким звернувся клієнт. */
enum class OrderChannel(val key: String, private val labelKey: String, val tag: String) {
    INSTAGRAM("instagram", "Instagram", "IG"),
    WHATSAPP("whatsapp", "WhatsApp", "WA"),
    TELEGRAM("telegram", "Telegram", "TG"),
    CALL("call", tk("Дзвінок"), "📞"),
    ;

    val label: String get() = tr(labelKey)

    companion object {
        fun of(k: String?): OrderChannel = entries.firstOrNull { it.key == k } ?: CALL
    }
}

/** Замовлення на ремонт. */
data class Order(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val deviceType: String = "",
    val model: String = "",
    val clientName: String = tk("Невідомо"),
    val phone: String = "",
    val channel: String = "call",
    val description: String = "",
    val status: String = "red",
    val partsCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
    val photos: List<String> = emptyList(),
)

/** Деталь на складі майстерні. */
data class PartItem(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val name: String,
    val quantity: Int = 0,
    val location: String = "",   // № ящика / полиці
    val code: String = "",       // QR / штрих-код
    val note: String = "",
    val price: Double = 0.0,     // необовʼязково
)

/** Підсумок бухгалтерії. Чистий прибуток = оборот − запчастини (БЕЗ −10%). */
class AccountStats(
    val count: Int,
    val parts: Double,
    val revenue: Double,
    val net: Double,
    val orders: List<Order>,
)

object Crm {

    fun orderNo(id: Long): String = "#" + id.toString().padStart(4, '0')

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    /** Прибуток одного замовлення: оплата − запчастини (без −10%). */
    fun orderProfit(o: Order): Double = round2(o.totalCost - o.partsCost)

    /** Чистий прибуток після власного відсотка (резерв/податок). percent=0 → без змін. */
    fun afterReserve(net: Double, percent: Double): Double {
        val p = percent.coerceIn(0.0, 100.0)
        return round2(net * (1.0 - p / 100.0))
    }

    /**
     * Бухгалтерія: рахує ТІЛЬКИ замовлення зі статусом «Готово» (не з корзини) за період.
     * Запчастини рахуються окремо, чистий прибуток = оборот − запчастини (−10% прибрано).
     */
    fun accounting(orders: List<Order>, from: Long?, to: Long?): AccountStats {
        val done = orders.filter {
            !it.deleted && OrderStatus.of(it.status) == OrderStatus.GREEN &&
                run {
                    val ts = it.completedAt ?: it.updatedAt
                    (from == null || ts >= from) && (to == null || ts < to)
                }
        }
        val parts = done.sumOf { it.partsCost }
        val revenue = done.sumOf { it.totalCost }
        return AccountStats(done.size, round2(parts), round2(revenue), round2(revenue - parts), done)
    }

    /** Межі періоду: all / month / prevMonth. */
    fun periodRange(period: String, now: Long = System.currentTimeMillis()): Pair<Long?, Long?> {
        if (period == "all") return null to null
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis
        return when (period) {
            "month" -> monthStart to null
            "prevMonth" -> {
                val to = monthStart
                cal.add(Calendar.MONTH, -1)
                cal.timeInMillis to to
            }
            else -> null to null
        }
    }

    /** Грошовий формат: «1 234 ₴» (пробіл як роздільник тисяч). */
    fun money(value: Double): String {
        val rounded = Math.round(value)
        val neg = rounded < 0
        val digits = kotlin.math.abs(rounded).toString()
        val sb = StringBuilder()
        for ((i, ch) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(' ')
            sb.append(ch)
        }
        return (if (neg) "-" else "") + sb.toString() + tr(" ₴")
    }
}
