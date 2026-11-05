package ua.savosh.ecoflowdiag.net

import android.content.Context
import ua.savosh.ecoflowdiag.BuildConfig

/** Чи справжня це копія застосунку: перевірка підпису проти офіційного сертифіката Savosh. */
object AppIntegrity {
    private const val OFFICIAL_CERT = "a7a334cf3c2b5dc9cf1485ef714922dc12d0c8685abe41d441672ca06cb893f2"

    /** true — реліз підписано чужим ключем: хтось перезібрав застосунок, і вводити в нього пароль небезпечно. */
    fun isForeignBuild(ctx: Context): Boolean {
        if (BuildConfig.DEBUG) return false
        val signers = ApkUpdater.ownSigners(ctx)
        return signers.isNotEmpty() && OFFICIAL_CERT !in signers
    }
}
