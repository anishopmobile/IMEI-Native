package ir.anishop.imei

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.anishop.imei.backup.PortableBackup
import ir.anishop.imei.data.LocalDb
import ir.anishop.imei.data.TradeRepository
import ir.anishop.imei.domain.*
import ir.anishop.imei.security.AuthService
import ir.anishop.imei.security.LocalVault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupIntegrityTest {
    @Test fun encryptedBackupRoundTripAndTamperingIsRejected() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LocalDb::class.java).build()
        try {
            val auth = AuthService(db)
            auth.setup("مدیر تست", "admin_test", "test@example.com", "This-Is-A-Strong-Pass-1")
            auth.signIn("admin_test", "This-Is-A-Strong-Pass-1")
            val vault = LocalVault()
            val repo = TradeRepository(db, auth, vault)
            repo.create(TradeDraft(imei1 = "490154203237518", kind = TradeKind.BUY,
                dateIso = PersianDates.today(), firstName = "مریم", lastName = "رضایی",
                nationalId = "0084575948", model = "مدل تست", color = "سفید",
                memory = "128GB", condition = PhoneCondition.NEW, registration = RegisterType.COMPANY))
            val manager = PortableBackup(db, auth, vault)
            val pass = "Backup-Strong-Password-2026".toCharArray()
            val bytes = manager.export(pass)
            assertTrue(manager.preview(bytes, pass).contains("1"))
            val corrupt = bytes.clone().also { it[it.lastIndex] = (it.last().toInt() xor 0x7F).toByte() }
            assertTrue(runCatching { manager.preview(corrupt, pass) }.isFailure)
            manager.restore(bytes, pass)
            assertEquals(1, db.imei().allTrades().size)
            assertEquals(1, db.imei().allAliases().size)
            assertEquals(1, db.imei().allAudits().size)
        } finally { db.close() }
    }
}
