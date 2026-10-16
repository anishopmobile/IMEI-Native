package ir.anishop.imei.backup

import androidx.room.withTransaction
import ir.anishop.imei.data.*
import ir.anishop.imei.domain.Rules
import ir.anishop.imei.domain.TradeDraft
import ir.anishop.imei.domain.TradeValidation
import ir.anishop.imei.domain.TradeKind
import ir.anishop.imei.domain.PhoneCondition
import ir.anishop.imei.domain.RegisterType
import ir.anishop.imei.security.AuthService
import ir.anishop.imei.security.LocalVault
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer

/** Passphrase-encrypted, cross-device portable snapshot (users deliberately excluded). */
class PortableBackup(private val db: LocalDb, private val auth: AuthService, private val vault: LocalVault) {
    private val magic = "IMEI-LOCAL-BACKUP-1\n".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()
    private fun derive(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass, salt, 310_000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
    private fun tradeJson(t: TradeEntity): JSONObject = JSONObject().apply {
        put("id", t.id); put("phoneId", t.phoneId); put("kind", t.kind); put("dateIso", t.dateIso)
        put("firstName", t.firstName); put("lastName", t.lastName); put("nationalId", t.nationalId)
        put("contact", t.contact); put("model", t.model); put("color", t.color); put("memory", t.memory)
        put("condition", t.condition); put("registration", t.registration); put("notes", t.notes)
        put("createdBy", t.createdBy); put("createdName", t.createdName); put("updatedBy", t.updatedBy)
        put("updatedName", t.updatedName); put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
        put("deletedAt", t.deletedAt ?: JSONObject.NULL); put("version", t.version)
    }
    suspend fun export(password: CharArray): ByteArray {
        auth.requireAdmin(); require(password.size >= 12) { "رمز پشتیبان حداقل ۱۲ نویسه باشد" }
        val dao = db.imei()
        val data = db.withTransaction {
            JSONObject().apply {
                put("schema", 1)
                put("phones", JSONArray().also { a -> dao.allPhones().forEach { p -> a.put(JSONObject().put("id", p.id).put("createdAt", p.createdAt)) } })
                put("aliases", JSONArray().also { a -> dao.allAliases().forEach { p -> a.put(JSONObject().put("imei", p.imei).put("phoneId", p.phoneId).put("slot", p.slot)) } })
                put("trades", JSONArray().also { a -> dao.allTrades().forEach { t -> a.put(tradeJson(t)) } })
                put("audit", JSONArray().also { a -> dao.allAudits().forEach { t -> a.put(JSONObject().put("id", t.id).put("tradeId", t.tradeId).put("action", t.action).put("actorId", t.actorId).put("actorName", t.actorName).put("at", t.at).put("snapshot", vault.open(t.snapshot))) } })
            }
        }.toString().toByteArray(Charsets.UTF_8)
        require(data.size <= 15_000_000) { "حجم پشتیبان بیش از حد مجاز است" }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(magic)
        return magic + salt + iv + cipher.doFinal(data)
    }
    private data class Data(val phones: List<PhoneEntity>, val aliases: List<AliasEntity>, val trades: List<TradeEntity>, val audits: List<AuditEntity>)
    private fun parse(bytes: ByteArray, password: CharArray): Data {
        require(bytes.size in (magic.size + 16 + 12 + 16)..15_000_100) { "حجم فایل نامعتبر است" }
        require(bytes.copyOfRange(0, magic.size).contentEquals(magic)) { "فرمت پشتیبان پشتیبانی نمی‌شود" }
        val offset = magic.size
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(password, bytes.copyOfRange(offset, offset + 16)), GCMParameterSpec(128, bytes.copyOfRange(offset + 16, offset + 28)))
        cipher.updateAAD(magic)
        val jsonBytes = cipher.doFinal(bytes.copyOfRange(offset + 28, bytes.size))
        val root = JSONObject(String(jsonBytes, Charsets.UTF_8))
        require(root.getInt("schema") == 1) { "نسخه پشتیبان پشتیبانی نمی‌شود" }
        fun <T> read(name: String, limit: Int, convert: (JSONObject)->T): List<T> {
            val array = root.getJSONArray(name)
            require(array.length() <= limit) { "تعداد رکوردها بیش از حد مجاز است" }
            return (0 until array.length()).map { convert(array.getJSONObject(it)) }
        }
        val phones = read("phones", 5000) { PhoneEntity(it.getString("id"), it.getLong("createdAt")) }
        val aliases = read("aliases", 10000) { AliasEntity(it.getString("imei"), it.getString("phoneId"), it.getInt("slot")) }
        val trades = read("trades", 5000) { j -> TradeEntity(
            j.getString("id"), j.getString("phoneId"), j.getString("kind"), j.getString("dateIso"),
            j.getString("firstName"), j.getString("lastName"), j.getString("nationalId"), j.getString("contact"),
            j.getString("model"), j.getString("color"), j.getString("memory"), j.getString("condition"),
            j.getString("registration"), j.getString("notes"), j.getString("createdBy"), j.getString("createdName"),
            j.getString("updatedBy"), j.getString("updatedName"), j.getLong("createdAt"), j.getLong("updatedAt"),
            if (j.isNull("deletedAt")) null else j.getLong("deletedAt"), j.getInt("version")) }
        val audits = read("audit", 30000) { j -> AuditEntity(j.getString("id"), j.getString("tradeId"), j.getString("action"), j.getString("actorId"), j.getString("actorName"), j.getLong("at"), j.getString("snapshot")) }
        require(phones.map { it.id }.distinct().size == phones.size)
        require(aliases.map { it.imei }.distinct().size == aliases.size)
        require(aliases.map { it.phoneId to it.slot }.distinct().size == aliases.size)
        require(trades.map { it.id }.distinct().size == trades.size)
        require(audits.map { it.id }.distinct().size == audits.size)
        val phoneIds = phones.map { it.id }.toSet()
        val tradeIds = trades.map { it.id }.toSet()
        require(aliases.all { it.phoneId in phoneIds && it.slot in 1..2 && Rules.imei(it.imei) })
        require(phones.all { p -> aliases.count { it.phoneId == p.id && it.slot == 1 } == 1 })
        require(trades.all { t ->
            t.phoneId in phoneIds && t.version > 0 &&
            runCatching { TradeValidation.validate(TradeDraft(
                imei1 = aliases.first { it.phoneId == t.phoneId && it.slot == 1 }.imei,
                imei2 = aliases.firstOrNull { it.phoneId == t.phoneId && it.slot == 2 }?.imei.orEmpty(),
                kind = TradeKind.valueOf(t.kind.uppercase()), dateIso = t.dateIso,
                firstName = t.firstName, lastName = t.lastName, nationalId = t.nationalId,
                contact = t.contact, model = t.model, color = t.color, memory = t.memory,
                condition = PhoneCondition.valueOf(t.condition.uppercase()),
                registration = RegisterType.valueOf(t.registration.uppercase()), notes = t.notes
            )).isEmpty() }.getOrDefault(false)
        }) { "اطلاعات معامله در پشتیبان نامعتبر است" }
        require(audits.all { it.tradeId in tradeIds && it.snapshot.length <= 20_000 && runCatching { JSONObject(it.snapshot) }.isSuccess })
        return Data(phones, aliases, trades, audits)
    }
    /** Preview/validation does not mutate database. Caller must request explicit restore confirmation. */
    suspend fun preview(bytes: ByteArray, password: CharArray): String {
        auth.requireAdmin()
        val data = parse(bytes, password)
        return "${data.phones.size} دستگاه، ${data.trades.size} معامله، ${data.audits.size} رویداد. بازیابی جایگزین داده‌های فعلی می‌شود؛ کاربران محلی حفظ می‌شوند."
    }
    suspend fun restore(bytes: ByteArray, password: CharArray) {
        auth.requireAdmin()
        val data = parse(bytes, password)
        db.withTransaction {
            val d = db.imei()
            d.clearAudit(); d.clearTrades(); d.clearAliases(); d.clearPhones()
            data.phones.forEach { d.addPhone(it) }
            data.aliases.forEach { d.addAlias(it) }
            data.trades.forEach { d.insertTrade(it) }
            data.audits.forEach { d.insertAudit(it.copy(snapshot = vault.seal(it.snapshot))) }
        }
    }
}
