package ir.anishop.imei.domain

import org.junit.Assert.*
import org.junit.Test

class BarcodeParserTest {
    @Test fun onlyVerifiedImeis() {
        assertEquals("490154203237518", BarcodeParser.parse("IMEI: 490154203237518 / SN: AB456").first().imei)
        assertTrue(BarcodeParser.parse("IMEI: 490154203237519").isEmpty())
    }
}
