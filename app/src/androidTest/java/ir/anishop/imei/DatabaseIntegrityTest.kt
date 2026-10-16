package ir.anishop.imei

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.anishop.imei.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseIntegrityTest {
    @Test fun uniqueImeiAcrossDifferentPhonesAndRollback() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(app, LocalDb::class.java).build()
        try {
            val dao = db.imei()
            dao.addPhone(PhoneEntity("p1", 1))
            dao.addPhone(PhoneEntity("p2", 2))
            val attempt = runCatching {
                db.withTransaction {
                    dao.addAlias(AliasEntity("490154203237518", "p1", 1))
                    dao.addAlias(AliasEntity("490154203237518", "p2", 1))
                }
            }
            assertTrue(attempt.isFailure)
            assertNull(dao.owner("490154203237518"))
        } finally { db.close() }
    }
    @Test fun persianLeapYearAndNewYearRoundTrip() {
        val leap = ir.anishop.imei.domain.PersianDates.fromPersian(1403, 12, 30)
        assertEquals(Triple(1403, 12, 30), ir.anishop.imei.domain.PersianDates.toPersian(leap))
        assertEquals(Triple(1404, 1, 1), ir.anishop.imei.domain.PersianDates.toPersian(leap.let { java.time.LocalDate.parse(it).plusDays(1).toString() }))
        assertTrue(runCatching { ir.anishop.imei.domain.PersianDates.fromPersian(1404, 12, 30) }.isFailure)
    }
}
