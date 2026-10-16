package ir.anishop.imei.domain

import android.icu.util.Calendar
import android.icu.util.ULocale
import android.icu.util.TimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** ICU PersianCalendar is provided by Android API 24+. ISO day is the database canonical representation. */
object PersianDates {
    private fun calendar(): Calendar = Calendar.getInstance(TimeZone.getDefault(), ULocale("fa_IR@calendar=persian")).apply { isLenient = false }
    fun today(): String = LocalDate.now().toString()
    fun toPersian(iso: String): Triple<Int, Int, Int> {
        val day = LocalDate.parse(iso)
        val millis = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return calendar().apply { timeInMillis = millis }.run {
            Triple(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))
        }
    }
    fun fromPersian(year: Int, month: Int, day: Int): String {
        require(year in 1300..1500 && month in 1..12 && day in 1..31) { "تاریخ شمسی معتبر نیست" }
        val c = calendar()
        c.clear()
        c.set(year, month - 1, day, 12, 0, 0)
        val ms = c.timeInMillis
        require(c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month - 1 && c.get(Calendar.DAY_OF_MONTH) == day)
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }
    fun display(iso: String): String = runCatching {
        val (y, m, d) = toPersian(iso)
        Rules.displayDigits("%04d/%02d/%02d".format(java.util.Locale.ROOT, y, m, d))
    }.getOrDefault("—")
    val months = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
}
