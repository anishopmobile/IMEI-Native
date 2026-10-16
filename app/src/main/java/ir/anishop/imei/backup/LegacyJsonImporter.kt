package ir.anishop.imei.backup

import androidx.room.withTransaction
import ir.anishop.imei.data.*
import ir.anishop.imei.domain.*
import ir.anishop.imei.security.AuthService
import ir.anishop.imei.security.LocalVault
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Explicit offline import from the original web application's JSON export (transactions array).
 * Any invalid row or alias conflict rolls back the entire batch; existing trade IDs are skipped.
 */
class LegacyJsonImporter(private val db: LocalDb, private val auth: AuthService, private val vault: LocalVault) {
    data class LegacyRow(val sourceId: String, val data: TradeDraft, val creatorName: String, val createdAt: Long?)
    data class Result(val imported: Int, val skipped: Int)
    private fun parse(source: ByteArray): List<LegacyRow> {
        require(source.size in 10..15_000_000) { "حجم فایل مناسب نیست" }
        val root = JSONObject(source.toString(Charsets.UTF_8))
        val array = root.getJSONArray("transactions")
        require(array.length() <= 5000) { "فایل بیش از ۵۰۰۰ معامله دارد" }
        val list = mutableListOf<LegacyRow>()
        for (i in 0 until array.length()) {
            val r = array.getJSONObject(i)
            val id = r.getString("id")
            require(id.matches(Regex("[a-fA-F0-9]{32}"))) { "شناسه ردیف ${i + 1} معتبر نیست" }
            val date = Rules.digits(r.getString("date"))
            val m = Regex("(\\d{4})/(\\d{2})/(\\d{2})").matchEntire(date) ?: error("تاریخ معامله ${i + 1} معتبر نیست")
            val iso = PersianDates.fromPersian(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            val trade = TradeDraft(
                imei1 = Rules.digits(r.getString("imei1")), imei2 = Rules.digits(r.optString("imei2")),
                kind = TradeKind.valueOf(r.getString("kind").uppercase()), dateIso = iso,
                firstName = r.getString("first_name"), lastName = r.getString("last_name"),
                nationalId = Rules.digits(r.getString("national_id")), contact = Rules.digits(r.optString("contact")),
                model = r.getString("model"), color = r.getString("color"), memory = r.getString("memory"),
                condition = PhoneCondition.valueOf(r.getString("condition").uppercase()),
                registration = RegisterType.valueOf(r.getString("registration").uppercase()),
                notes = r.optString("notes", "")
            )
            val errs = TradeValidation.validate(trade)
            require(errs.isEmpty()) { "ردیف ${i + 1}: ${errs.values.firstOrNull()}" }
            val created = runCatching { Instant.parse(r.optString("created_at")).toEpochMilli() }.getOrNull()
            list += LegacyRow(id, trade, r.optString("created_name", "واردشده از پشتیبان").take(100), created)
        }
        require(list.map { it.sourceId }.distinct().size == list.size) { "شناسه معامله در فایل تکراری است" }
        return list
    }
    suspend fun preview(source: ByteArray): String {
        auth.requireAdmin()
        return "${parse(source).size} معامله معتبر از نسخه وب شناسایی شد. واردکردن داده‌ها به‌صورت افزایشی است؛ هیچ داده قبلی حذف نخواهد شد. شناسه‌های تکراری رد می‌شوند."
    }
    suspend fun importLegacy(source: ByteArray): Result {
        val user = auth.requireAdmin()
        val rows = parse(source)
        return db.withTransaction {
            val dao = db.imei()
            var imported = 0
            var skipped = 0
            for (row in rows) {
                if (dao.tradeEntity(row.sourceId) != null) { skipped++; continue }
                val d = row.data
                val first = d.imei1
                val second = d.imei2.takeIf { it.isNotEmpty() }
                val p1 = dao.owner(first)
                val p2 = second?.let { dao.owner(it) }
                if (p1 != null && p2 != null && p1 != p2) error("دو IMEI به دو دستگاه مختلف تعلق دارند")
                val phoneId = p1 ?: p2 ?: UUID.randomUUID().toString().also { dao.addPhone(PhoneEntity(it, System.currentTimeMillis())) }
                val known = dao.aliases(phoneId)
                if (known.isEmpty()) {
                    dao.addAlias(AliasEntity(first, phoneId, 1))
                    if (second != null) dao.addAlias(AliasEntity(second, phoneId, 2))
                } else {
                    val present = known.map { it.imei }.toSet()
                    val extra = listOfNotNull(first, second).distinct().filterNot { it in present }
                    require(extra.size <= 1 && known.size + extra.size <= 2) { "دو شناسه با دستگاه موجود سازگار نیستند" }
                    if (extra.isNotEmpty()) dao.addAlias(AliasEntity(extra.first(), phoneId, 2))
                }
                val now = System.currentTimeMillis()
                val record = TradeEntity(
                    id = row.sourceId, phoneId = phoneId, kind = d.kind.name.lowercase(), dateIso = d.dateIso,
                    firstName = d.firstName.trim(), lastName = d.lastName.trim(), nationalId = d.nationalId,
                    contact = d.contact, model = d.model.trim(), color = d.color.trim(), memory = d.memory.trim(),
                    condition = d.condition!!.name.lowercase(), registration = d.registration!!.name.lowercase(), notes = d.notes,
                    createdBy = user.id, createdName = row.creatorName, updatedBy = user.id, updatedName = user.name,
                    createdAt = row.createdAt ?: now, updatedAt = now
                )
                dao.insertTrade(record)
                val auditText = JSONObject().put("source", "legacy-json").put("originalId", row.sourceId).toString()
                dao.insertAudit(AuditEntity(UUID.randomUUID().toString(), row.sourceId, "import", user.id, user.name, now, vault.seal(auditText)))
                imported++
            }
            Result(imported, skipped)
        }
    }
}
