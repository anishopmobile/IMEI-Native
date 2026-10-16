package ir.anishop.imei.domain

/** All input rules live here, independent of Compose widgets. */
object Rules {
    fun displayDigits(input: String): String = input.map { c -> if (c in '0'..'9') ('۰'.code + c.code - '0'.code).toChar() else c }.joinToString("")
    fun digits(input: String): String = buildString {
        input.trim().forEach { c -> append(when (c) {
            in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
            in '٠'..'٩' -> ('0'.code + c.code - '٠'.code).toChar()
            else -> c
        }) }
    }
    fun imei(raw: String): Boolean {
        val s = digits(raw)
        if (s.length != 15 || s.any { it !in '0'..'9' }) return false
        return luhn(s)
    }
    private fun luhn(s: String): Boolean {
        var sum = 0
        for (i in s.indices) {
            var n = s[i] - '0'
            if (i % 2 == 1) { n *= 2; if (n > 9) n -= 9 }
            sum += n
        }
        return sum % 10 == 0
    }
    fun nationalId(raw: String): Boolean {
        val s = digits(raw)
        if (s.length != 10 || s.any { it !in '0'..'9' } || s.toSet().size == 1) return false
        val n = (0..8).sumOf { (s[it] - '0') * (10 - it) } % 11
        val expected = if (n < 2) n else 11 - n
        return s[9] - '0' == expected
    }
    fun contact(raw: String): Boolean {
        val s = digits(raw)
        return s.isEmpty() || Regex("\\+?[0-9]{7,15}").matches(s)
    }
    fun trimmed(raw: String, max: Int = 100) = raw.trim().takeIf { it.isNotEmpty() && it.length <= max }
}

enum class TradeKind(val fa: String) { BUY("خرید"), SELL("فروش") }
enum class PhoneCondition(val fa: String) { NEW("آکبند"), USED("کارکرده") }
enum class RegisterType(val fa: String) { COMPANY("رجیستر شرکتی"), PASSENGER("رجیستر مسافری"), NONE("بدون رجیستر") }

data class TradeDraft(
    val imei1: String = "", val imei2: String = "", val kind: TradeKind = TradeKind.BUY,
    val dateIso: String = "", val firstName: String = "", val lastName: String = "",
    val nationalId: String = "", val contact: String = "", val model: String = "",
    val color: String = "", val memory: String = "", val condition: PhoneCondition? = null,
    val registration: RegisterType? = null, val notes: String = ""
)
object TradeValidation {
    /** Errors keyed by field; retain draft until successful persistence. */
    fun validate(d: TradeDraft): Map<String, String> = buildMap {
        if (!Rules.imei(d.imei1)) put("imei1", "IMEI اول باید ۱۵ رقم و دارای رقم کنترلی صحیح باشد")
        if (d.imei2.isNotBlank() && !Rules.imei(d.imei2)) put("imei2", "IMEI دوم صحیح نیست")
        if (d.imei2.isNotBlank() && Rules.digits(d.imei1) == Rules.digits(d.imei2)) put("imei2", "دو IMEI نباید یکسان باشند")
        if (Rules.trimmed(d.model) == null) put("model", "مدل گوشی را وارد کنید")
        if (Rules.trimmed(d.color) == null) put("color", "رنگ گوشی را وارد کنید")
        if (Rules.trimmed(d.memory) == null) put("memory", "حافظه گوشی را وارد کنید")
        if (d.condition == null) put("condition", "وضعیت گوشی را انتخاب کنید")
        if (d.registration == null) put("registration", "وضعیت رجیستر را انتخاب کنید")
        if (d.dateIso.isBlank()) put("dateIso", "تاریخ معامله را انتخاب کنید")
        else if (runCatching { java.time.LocalDate.parse(d.dateIso) }.isFailure) put("dateIso", "تاریخ معامله معتبر نیست")
        if (!Rules.nationalId(d.nationalId)) put("nationalId", "کد ملی صحیح نیست")
        if (!Rules.contact(d.contact)) put("contact", "شماره تماس صحیح نیست")
        if (Rules.trimmed(d.firstName) == null) put("firstName", "نام را وارد کنید")
        if (Rules.trimmed(d.lastName) == null) put("lastName", "نام خانوادگی را وارد کنید")
        if (d.notes.length > 5000) put("notes", "توضیحات بیش از ۵۰۰۰ نویسه است")
    }
}
