package ir.anishop.imei.data

import androidx.room.*

@Entity(tableName = "phones")
data class PhoneEntity(@PrimaryKey val id: String, val createdAt: Long)

@Entity(tableName = "aliases", foreignKeys = [ForeignKey(entity = PhoneEntity::class, parentColumns = ["id"], childColumns = ["phoneId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("phoneId"), Index(value = ["phoneId", "slot"], unique = true)])
data class AliasEntity(@PrimaryKey val imei: String, val phoneId: String, val slot: Int)

@Entity(tableName = "trades", foreignKeys = [ForeignKey(entity = PhoneEntity::class, parentColumns = ["id"], childColumns = ["phoneId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("phoneId"), Index("dateIso"), Index("nationalId")])
data class TradeEntity(
    @PrimaryKey val id: String, val phoneId: String, val kind: String, val dateIso: String,
    val firstName: String, val lastName: String, val nationalId: String, val contact: String,
    val model: String, val color: String, val memory: String, val condition: String,
    val registration: String, val notes: String, val createdBy: String, val createdName: String,
    val updatedBy: String, val updatedName: String, val createdAt: Long, val updatedAt: Long,
    val deletedAt: Long? = null, val version: Int = 1
)

@Entity(tableName = "audit", foreignKeys = [ForeignKey(entity = TradeEntity::class, parentColumns = ["id"], childColumns = ["tradeId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("tradeId")])
data class AuditEntity(@PrimaryKey val id: String, val tradeId: String, val action: String, val actorId: String, val actorName: String, val at: Long, val snapshot: String)

@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true), Index(value = ["email"], unique = true)])
data class UserEntity(@PrimaryKey val id: String, val name: String, val username: String, val email: String, val role: String, val active: Boolean, val hash: String, val salt: String, val failed: Int = 0, val lockedUntil: Long = 0L, val sessionVersion: Int = 1)

data class PhoneRow(val phoneId: String, val imei1: String, val imei2: String?, val tradeCount: Int, val lastModel: String?, val lastKind: String?)
data class TradeRow(@Embedded val trade: TradeEntity, val imei1: String, val imei2: String?)

@Dao
interface ImeiDao {
    @Query("SELECT p.id AS phoneId, COALESCE((SELECT imei FROM aliases WHERE phoneId=p.id AND slot=1),'') AS imei1, (SELECT imei FROM aliases WHERE phoneId=p.id AND slot=2) AS imei2, (SELECT count(*) FROM trades WHERE phoneId=p.id AND deletedAt IS NULL) AS tradeCount, (SELECT model FROM trades WHERE phoneId=p.id AND deletedAt IS NULL ORDER BY dateIso DESC,createdAt DESC LIMIT 1) AS lastModel, (SELECT kind FROM trades WHERE phoneId=p.id AND deletedAt IS NULL ORDER BY dateIso DESC,createdAt DESC LIMIT 1) AS lastKind FROM phones p ORDER BY p.createdAt DESC")
    fun phones(): kotlinx.coroutines.flow.Flow<List<PhoneRow>>
    @Query("SELECT p.id AS phoneId, COALESCE((SELECT imei FROM aliases WHERE phoneId=p.id AND slot=1),'') AS imei1, (SELECT imei FROM aliases WHERE phoneId=p.id AND slot=2) AS imei2, (SELECT count(*) FROM trades WHERE phoneId=p.id AND deletedAt IS NULL) AS tradeCount, (SELECT model FROM trades WHERE phoneId=p.id AND deletedAt IS NULL ORDER BY dateIso DESC,createdAt DESC LIMIT 1) AS lastModel, (SELECT kind FROM trades WHERE phoneId=p.id AND deletedAt IS NULL ORDER BY dateIso DESC,createdAt DESC LIMIT 1) AS lastKind FROM phones p WHERE p.id=:id LIMIT 1")
    suspend fun phone(id: String): PhoneRow?
    @Query("SELECT phoneId FROM aliases WHERE imei=:imei LIMIT 1") suspend fun owner(imei: String): String?
    @Query("SELECT * FROM aliases WHERE phoneId=:id ORDER BY slot") suspend fun aliases(id: String): List<AliasEntity>
    @Insert suspend fun addPhone(phone: PhoneEntity)
    @Insert suspend fun addAlias(alias: AliasEntity)
    @Query("SELECT t.*, (SELECT imei FROM aliases WHERE phoneId=t.phoneId AND slot=1) AS imei1, (SELECT imei FROM aliases WHERE phoneId=t.phoneId AND slot=2) AS imei2 FROM trades t WHERE t.deletedAt IS NULL ORDER BY t.dateIso DESC,t.createdAt DESC")
    fun liveTrades(): kotlinx.coroutines.flow.Flow<List<TradeRow>>
    @Query("SELECT t.*, (SELECT imei FROM aliases WHERE phoneId=t.phoneId AND slot=1) AS imei1, (SELECT imei FROM aliases WHERE phoneId=t.phoneId AND slot=2) AS imei2 FROM trades t WHERE t.deletedAt IS NOT NULL ORDER BY t.updatedAt DESC")
    fun deletedTrades(): kotlinx.coroutines.flow.Flow<List<TradeRow>>
    @Query("SELECT t.*, (SELECT imei FROM aliases WHERE phoneId=t.phoneId AND slot=1) AS imei1, (SELECT imei FROM aliases WHERE phoneId=t.phoneId AND slot=2) AS imei2 FROM trades t WHERE t.id=:id LIMIT 1")
    suspend fun trade(id: String): TradeRow?
    @Query("SELECT * FROM trades WHERE id=:id") suspend fun tradeEntity(id: String): TradeEntity?
    @Insert suspend fun insertTrade(t: TradeEntity)
    @Update suspend fun updateTrade(t: TradeEntity)
    @Query("UPDATE trades SET deletedAt=:deletedAt, updatedAt=:now, updatedBy=:actorId, updatedName=:actorName, version=version+1 WHERE id=:id AND version=:version")
    suspend fun changeDeletion(id: String, version: Int, deletedAt: Long?, now: Long, actorId: String, actorName: String): Int
    @Insert suspend fun insertAudit(a: AuditEntity)
    @Query("SELECT * FROM audit WHERE tradeId=:id ORDER BY at DESC") fun audits(id: String): kotlinx.coroutines.flow.Flow<List<AuditEntity>>
    @Query("SELECT COUNT(*) FROM phones") fun phoneCount(): kotlinx.coroutines.flow.Flow<Int>
    @Query("SELECT COUNT(*) FROM trades WHERE kind=:kind AND deletedAt IS NULL AND dateIso=:day") fun dayCount(kind: String, day: String): kotlinx.coroutines.flow.Flow<Int>
    @Query("SELECT * FROM phones") suspend fun allPhones(): List<PhoneEntity>
    @Query("SELECT * FROM aliases") suspend fun allAliases(): List<AliasEntity>
    @Query("SELECT * FROM trades") suspend fun allTrades(): List<TradeEntity>
    @Query("SELECT * FROM audit") suspend fun allAudits(): List<AuditEntity>
    @Query("DELETE FROM audit") suspend fun clearAudit()
    @Query("DELETE FROM trades") suspend fun clearTrades()
    @Query("DELETE FROM aliases") suspend fun clearAliases()
    @Query("DELETE FROM phones") suspend fun clearPhones()
}

@Entity(tableName = "user_events", indices = [Index("userId")])
data class UserEventEntity(@PrimaryKey val id: String, val userId: String, val action: String, val actorId: String, val at: Long)

@Dao
interface AuthDao {
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
    @Query("SELECT * FROM users WHERE username=:username LIMIT 1") suspend fun byName(username: String): UserEntity?
    @Query("SELECT * FROM users WHERE id=:id LIMIT 1") suspend fun byId(id: String): UserEntity?
    @Query("SELECT * FROM users ORDER BY role,name") fun all(): kotlinx.coroutines.flow.Flow<List<UserEntity>>
    @Insert suspend fun add(u: UserEntity)
    @Insert suspend fun addEvent(event: UserEventEntity)
    @Query("SELECT * FROM user_events ORDER BY at DESC LIMIT 100") fun events(): kotlinx.coroutines.flow.Flow<List<UserEventEntity>>
    @Update suspend fun update(u: UserEntity)
    @Query("UPDATE users SET active=:active, sessionVersion=sessionVersion+1 WHERE id=:id AND role!='admin'") suspend fun active(id: String, active: Boolean): Int
}

@Database(entities = [PhoneEntity::class, AliasEntity::class, TradeEntity::class, AuditEntity::class, UserEntity::class, UserEventEntity::class], version = 1, exportSchema = true)
abstract class LocalDb : RoomDatabase() {
    abstract fun imei(): ImeiDao
    abstract fun users(): AuthDao
}
