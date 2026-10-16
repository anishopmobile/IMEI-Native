package ir.anishop.imei

import android.app.Application
import androidx.room.Room
import ir.anishop.imei.data.LocalDb
import ir.anishop.imei.data.TradeRepository
import ir.anishop.imei.security.AuthService
import ir.anishop.imei.security.DisplayPreferences
import ir.anishop.imei.security.LocalVault

class ImeiApplication : Application() {
    @Volatile var lastBackgroundAt: Long = 0L
    val database by lazy { Room.databaseBuilder(this, LocalDb::class.java, "imei_private.db")
        .build() }
    val preferences by lazy { DisplayPreferences(this) }
    val auth by lazy { AuthService(database) }
    val vault by lazy { LocalVault() }
    val trades by lazy { TradeRepository(database, auth, vault) }
}
