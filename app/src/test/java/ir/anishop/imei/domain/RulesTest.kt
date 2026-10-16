package ir.anishop.imei.domain

import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test fun acceptsLuhnImeisAndPersianDigits() {
        assertTrue(Rules.imei("490154203237518"))
        assertTrue(Rules.imei("۴۹۰۱۵۴۲۰۳۲۳۷۵۱۸"))
        assertFalse(Rules.imei("490154203237519"))
        assertFalse(Rules.imei("49015420323751"))
    }
    @Test fun validatesIranianNationalId() {
        assertTrue(Rules.nationalId("0084575948"))
        assertTrue(Rules.nationalId("۰۰۸۴۵۷۵۹۴۸"))
        assertFalse(Rules.nationalId("0084575949"))
        assertFalse(Rules.nationalId("1111111111"))
    }
    @Test fun secondIdentifierIsOptional() {
        val draft = valid()
        assertTrue(TradeValidation.validate(draft).isEmpty())
        assertEquals("کد ملی صحیح نیست", TradeValidation.validate(draft.copy(nationalId = "0084575949"))["nationalId"])
        assertTrue(TradeValidation.validate(draft.copy(imei2 = draft.imei1)).containsKey("imei2"))
    }
    @Test fun parsesLabelledBarcodeButNotSerial() {
        assertEquals(listOf("490154203237518"), BarcodeParser.parse("SERIAL 123456789012345 IMEI1: 490154203237518").map { it.imei })
        assertTrue(BarcodeParser.parse("12345678901234").isEmpty())
    }
    @Test fun acceptsTelephoneFormatsFromOriginalProject() {
        assertTrue(Rules.contact(""))
        assertTrue(Rules.contact("۰۹۱۲۳۴۵۶۷۸۹"))
        assertTrue(Rules.contact("+989123456789"))
        assertTrue(Rules.contact("123456789012345"))
        assertFalse(Rules.contact("123456"))
        assertFalse(Rules.contact("0912-3456789"))
    }
    @Test fun validatesNamesAndNotes() {
        assertTrue(TradeValidation.validate(valid().copy(firstName = "   ")).containsKey("firstName"))
        assertTrue(TradeValidation.validate(valid().copy(notes = "س".repeat(5001))).containsKey("notes"))
    }
    private fun valid() = TradeDraft(imei1 = "490154203237518", dateIso = "2026-10-16", firstName = "مریم", lastName = "احمدی", nationalId = "0084575948", model = "Pixel", color = "سفید", memory = "128GB", condition = PhoneCondition.NEW, registration = RegisterType.COMPANY)
}
