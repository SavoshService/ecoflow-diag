package ua.savosh.ecoflowdiag.core

import kotlin.math.sin

/**
 * Демо-станція: несправжні дані, щоб роздивитися можливості програми без реальної станції.
 * Модель — як Delta 2 Max (2-ге покоління), одна батарея, у стані розряду, з кодом 23 (щоб показати пояснення).
 */
object Demo {
    const val SN = "R351ZEBDEMO00000"

    /** Параметри демо-станції. [t] — крок часу (секунди), щоб трохи «оживити» струм і заряд. */
    fun params(t: Double = 0.0): Map<String, Any?> {
        val soc = 54.0 + 6.0 * sin(t * 0.12)                 // ~48…60 %
        val outW = 260.0 + 90.0 * sin(t * 0.2 + 1.0)          // ~170…350 Вт навантаження
        val vol = 51200L + (soc * 10).toLong()                // трохи плаває з SOC
        val ampMa = -(outW / (vol / 1000.0) * 1000).toLong()  // від'ємний — розряд
        val base = 3235
        val cells = (0 until 16).map { i -> (base + (i % 5) * 4 + (if (i == 7) 14 else 0)).toLong() } // легкий дисбаланс, група 8 вище
        val remain = (40000 * soc / 100).toLong()
        return linkedMapOf(
            "bms_bmsStatus.soc" to soc.toLong(),
            "bms_bmsStatus.f32ShowSoc" to soc,
            "bms_bmsStatus.soh" to 92L,
            "bms_bmsStatus.designCap" to 40000L,
            "bms_bmsStatus.fullCap" to 36800L,
            "bms_bmsStatus.remainCap" to remain,
            "bms_bmsStatus.cycles" to 182L,
            "bms_bmsStatus.vol" to vol,
            "bms_bmsStatus.amp" to ampMa,
            "bms_bmsStatus.temp" to 28L,
            "bms_bmsStatus.cellVol" to cells,
            "bms_bmsStatus.minCellVol" to cells.min(),
            "bms_bmsStatus.maxCellVol" to cells.max(),
            "bms_bmsStatus.cellTemp" to listOf(28L, 29L, 28L, 27L),
            "bms_bmsStatus.minMosTemp" to 30L,
            "bms_bmsStatus.maxMosTemp" to 33L,
            "bms_bmsStatus.errCode" to 23L,
            "bms_bmsStatus.allErrCode" to 8388608L,
            "bms_bmsStatus.bmsFault" to 0L,
            "bms_bmsStatus.bqSysStatReg" to 128L,
            "bms_bmsStatus.mosState" to 2L,
            "bms_emsStatus.maxChargeSoc" to 100L,
            "bms_emsStatus.minDsgSoc" to 0L,
            "bms_emsStatus.emsIsNormalFlag" to 1L,
            "bms_emsStatus.bms0Online" to 3L,
            "bms_emsStatus.chgCmd" to 1L,
            "bms_emsStatus.dsgCmd" to 1L,
            "bms_emsStatus.lcdShowSoc" to soc.toLong(),
            "pd.iconBmsErrState" to 0L,
            "inv.outputWatts" to outW.toLong(),
            "inv.inputWatts" to 0L,
            "inv.cfgAcXboost" to 1L,
            "inv.cfgSlowChgWatts" to 800L,
            "pd.wattsOutSum" to outW.toLong(),
            "pd.wattsInSum" to 0L,
            "pd.invUsedTime" to 9_120_000L,
            "pd.dsgPowerAC" to 412_000L,
            "pd.chgPowerAC" to 780_000L,
            "pd.chgSunPower" to 210_000L,
            "pd.sysVer" to 16_909_056L,
            "pd.wifiVer" to 16_909_000L,
        )
    }
}
