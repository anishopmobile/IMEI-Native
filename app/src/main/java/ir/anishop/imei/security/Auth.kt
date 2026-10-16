package ir.anishop.imei.security

import ir.anishop.imei.data.*
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** No default users or credentials. Sessions are held only in memory. */
data class LocalSession(val id: String, val name: String, val role: String, val sessionVersion: Int)

class AuthService(private val db: LocalDb) {
    private val dao = db.users()
    private val random = SecureRandom()
    private val session = MutableStateFlow<LocalSession?>(null)
    val loggedIn: StateFlow<LocalSession?> = session
    suspend fun needsSetup() = dao.count() == 0
    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, 310_000, 256)
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    suspend fun setup(name: String, user: String, email: String, password: String) {
        require(dao.count() == 0) { "مدیر قبلاً ایجاد شده است" }
        require(password.length >= 10 && name.isNotBlank() && user.matches(Regex("[a-z0-9_.-]{3,60}"))) { "نام، کاربری یا رمز معتبر نیست" }
        require(validEmail(email)) { "ایمیل معتبر وارد کنید" }
        val admin = withContext(Dispatchers.Default) { newUser(name.trim(), user, email, password, "admin") }
        db.withTransaction {
            require(dao.count() == 0) { "مدیر قبلاً ساخته شده است" }
            dao.add(admin)
            dao.addEvent(UserEventEntity(java.util.UUID.randomUUID().toString(), admin.id, "add", admin.id, System.currentTimeMillis()))
        }
    }
    private fun validEmail(email: String): Boolean = email.matches(Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")) && email.length <= 254
    private fun newUser(name: String, username: String, email: String, password: String, role: String): UserEntity {
        val salt = ByteArray(16).also(random::nextBytes)
        val hash = derive(password.toCharArray(), salt)
        return UserEntity(java.util.UUID.randomUUID().toString(), name, username,
            email.trim().lowercase(), role, true, Base64.getEncoder().encodeToString(hash), Base64.getEncoder().encodeToString(salt))
    }
    suspend fun signIn(username: String, password: String) {
        val u = dao.byName(username.trim().lowercase()) ?: throw IllegalArgumentException("نام کاربری یا رمز نادرست است")
        val now = System.currentTimeMillis()
        if (u.lockedUntil > now) throw IllegalArgumentException("ورود موقتاً قفل است؛ ۱۵ دقیقه بعد تلاش کنید")
        val a = withContext(Dispatchers.Default) { derive(password.toCharArray(), Base64.getDecoder().decode(u.salt)) }
        val ok = MessageDigest.isEqual(a, Base64.getDecoder().decode(u.hash))
        if (!ok || !u.active) {
            val failures = u.failed + 1
            dao.update(u.copy(failed = failures, lockedUntil = if (failures >= 5) now + 15 * 60_000 else 0L))
            throw IllegalArgumentException("نام کاربری یا رمز نادرست است")
        }
        val clean = u.copy(failed = 0, lockedUntil = 0)
        dao.update(clean); session.value = LocalSession(clean.id, clean.name, clean.role, clean.sessionVersion)
    }
    fun logout() { session.value = null }
    suspend fun requireUser(): UserEntity {
        val old = session.value ?: throw SecurityException("ابتدا وارد شوید")
        val active = dao.byId(old.id)?.takeIf { it.active && it.sessionVersion == old.sessionVersion } ?: run {
            session.value = null
            throw SecurityException("نشست شما منقضی یا حساب غیرفعال شده است")
        }
        return active
    }
    suspend fun requireAdmin(): UserEntity = requireUser().also { if (it.role != "admin") throw SecurityException("فقط مدیر مجاز است") }
    suspend fun createStaff(name: String, username: String, email: String, password: String) {
        val actor = requireAdmin()
        val n = username.trim().lowercase()
        require(name.isNotBlank() && n.matches(Regex("[a-z0-9_.-]{3,60}")) && password.length >= 10 && validEmail(email))
        val added = withContext(Dispatchers.Default) { newUser(name.trim(), n, email, password, "staff") }
        db.withTransaction {
            dao.add(added)
            dao.addEvent(UserEventEntity(java.util.UUID.randomUUID().toString(), added.id, "add", actor.id, System.currentTimeMillis()))
        }
    }
    suspend fun setActive(id: String, active: Boolean) {
        val actor = requireAdmin()
        db.withTransaction {
            require(dao.active(id, active) == 1)
            dao.addEvent(UserEventEntity(java.util.UUID.randomUUID().toString(), id, if (active) "enable" else "disable", actor.id, System.currentTimeMillis()))
        }
    }
    suspend fun resetPassword(id: String, newPassword: String) {
        val actor = requireAdmin()
        require(newPassword.length >= 10) { "رمز جدید باید حداقل ۱۰ نویسه باشد" }
        val old = dao.byId(id) ?: throw IllegalArgumentException("کاربر پیدا نشد")
        require(old.role != "admin") { "رمز مدیر از این بخش قابل تغییر نیست" }
        val updated = withContext(Dispatchers.Default) { newUser(old.name, old.username, old.email, newPassword, old.role) }
        db.withTransaction {
            val fresh = dao.byId(id) ?: error("کاربر پیدا نشد")
            dao.update(fresh.copy(hash = updated.hash, salt = updated.salt, failed = 0, lockedUntil = 0, sessionVersion = fresh.sessionVersion + 1))
            dao.addEvent(UserEventEntity(java.util.UUID.randomUUID().toString(), id, "password", actor.id, System.currentTimeMillis()))
        }
    }
}
