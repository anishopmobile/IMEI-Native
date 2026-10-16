package ir.anishop.imei.data

import androidx.room.withTransaction
import ir.anishop.imei.domain.*
import ir.anishop.imei.security.AuthService
import ir.anishop.imei.security.LocalVault
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.flow.map

class TradeRepository(private val db: LocalDb, private val auth: AuthService, private val vault: LocalVault) {
    val phones = db.imei().phones()
    val trades = db.imei().liveTrades()
    val deleted = db.imei().deletedTrades().map { auth.requireAdmin(); it }
    val users = db.users().all()
    val userEvents = db.users().events()
    val phoneCount = db.imei().phoneCount()
    fun dayCount(kind: TradeKind, day: String) = db.imei().dayCount(kind.name.lowercase(), day)
    fun audits(tradeId: String) = db.imei().audits(tradeId)
    suspend fun trade(id: String): TradeRow? {
        val user = auth.requireUser()
        val row = db.imei().trade(id)
        return row?.takeIf { it.trade.deletedAt == null || user.role == "admin" }
    }
    suspend fun phone(id: String) = db.imei().phone(id)
    private fun id() = UUID.randomUUID().toString()
    private fun snap(t: TradeEntity): String = vault.seal(JSONObject().apply {
        put("id", t.id); put("kind", t.kind); put("model", t.model); put("nationalId", t.nationalId)
        put("dateIso", t.dateIso); put("version", t.version); put("deletedAt", t.deletedAt)
    }.toString())
    private fun columns(d: TradeDraft, phone: String, id: String, actorId: String, actorName: String, now: Long, old: TradeEntity? = null): TradeEntity = TradeEntity(
        id, phone, d.kind.name.lowercase(), d.dateIso, d.firstName.trim(), d.lastName.trim(), Rules.digits(d.nationalId),
        Rules.digits(d.contact), d.model.trim(), d.color.trim(), d.memory.trim(), d.condition!!.name.lowercase(),
        d.registration!!.name.lowercase(), d.notes, old?.createdBy ?: actorId, old?.createdName ?: actorName,
        actorId, actorName, old?.createdAt ?: now, now, old?.deletedAt, (old?.version ?: 0) + 1
    )
    private fun check(d: TradeDraft) {
        val errors = TradeValidation.validate(d)
        if (errors.isNotEmpty()) throw IllegalArgumentException(errors.values.first())
    }
    suspend fun create(d: TradeDraft): String {
        val user = auth.requireUser(); check(d)
        return db.withTransaction {
            val dao = db.imei()
            val first = Rules.digits(d.imei1)
            val second = Rules.digits(d.imei2).takeIf { it.isNotBlank() }
            val p1 = dao.owner(first)
            val p2 = second?.let { dao.owner(it) }
            if (p1 != null && p2 != null && p1 != p2) error("دو IMEI متعلق به دو گوشی متفاوت‌اند")
            val phone = p1 ?: p2 ?: id().also { dao.addPhone(PhoneEntity(it, System.currentTimeMillis())) }
            val existing = dao.aliases(phone)
            // Preserve the original PHP behaviour: either input may identify an existing
            // dual-SIM device; canonical IMEI slot ordering is stored on the phone.
            if (existing.isEmpty()) {
                dao.addAlias(AliasEntity(first, phone, 1))
                if (second != null) dao.addAlias(AliasEntity(second, phone, 2))
            } else {
                val present = existing.map { it.imei }.toSet()
                val extra = listOfNotNull(first, second).distinct().filterNot { it in present }
                require(extra.size <= 1 && existing.size + extra.size <= 2) {
                    "شماره‌های IMEI با شناسه‌های این دستگاه مطابقت ندارند"
                }
                if (extra.isNotEmpty()) dao.addAlias(AliasEntity(extra.first(), phone, 2))
            }
            val now = System.currentTimeMillis()
            val tid = id()
            val t = columns(d, phone, tid, user.id, user.name, now)
            dao.insertTrade(t)
            dao.insertAudit(AuditEntity(id(), tid, "create", user.id, user.name, now, snap(t)))
            tid
        }
    }
    suspend fun edit(tradeId: String, expectedVersion: Int, d: TradeDraft) {
        val user = auth.requireUser(); check(d)
        db.withTransaction {
            val dao = db.imei()
            val old = dao.tradeEntity(tradeId) ?: error("معامله یافت نشد")
            require(old.deletedAt == null && old.version == expectedVersion) { "نسخه معامله تغییر کرده است" }
            val aliases = dao.aliases(old.phoneId)
            require(Rules.digits(d.imei1) == aliases.first { it.slot == 1 }.imei &&
                Rules.digits(d.imei2) == (aliases.firstOrNull { it.slot == 2 }?.imei ?: "")) { "شناسه دستگاه پس از ثبت قابل تغییر نیست" }
            val now = System.currentTimeMillis()
            dao.updateTrade(columns(d, old.phoneId, old.id, user.id, user.name, now, old))
            dao.insertAudit(AuditEntity(id(), tradeId, "edit", user.id, user.name, now, snap(old)))
        }
    }
    suspend fun changeDeleted(tradeId: String, restore: Boolean) {
        val admin = auth.requireAdmin()
        db.withTransaction {
            val dao = db.imei()
            val old = dao.tradeEntity(tradeId) ?: error("معامله پیدا نشد")
            require((old.deletedAt != null) == restore) { "وضعیت معامله تغییر کرده است" }
            val now = System.currentTimeMillis()
            val changed = dao.changeDeletion(tradeId, old.version, if (restore) null else now, now, admin.id, admin.name)
            check(changed == 1) { "نسخه معامله تغییر کرده است" }
            dao.insertAudit(AuditEntity(id(), tradeId, if (restore) "restore" else "delete", admin.id, admin.name, now, snap(old)))
        }
    }
}
