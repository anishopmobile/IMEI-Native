package ir.anishop.imei.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.anishop.imei.ImeiApplication
import ir.anishop.imei.data.TradeRow
import ir.anishop.imei.domain.*
import ir.anishop.imei.domain.BarcodeParser.Candidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val root = app as ImeiApplication
    val auth = root.auth
    val preferences = root.preferences
    val repo = root.trades
    val database = root.database
    val vault = root.vault
    val initialized = MutableStateFlow(false)
    val needsSetup = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val draft = MutableStateFlow(TradeDraft(dateIso = PersianDates.today()))
    val errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val editing = MutableStateFlow<TradeRow?>(null)
    val search = MutableStateFlow("")
    val reportImei = MutableStateFlow("")
    val scanTarget = MutableStateFlow("imei1")
    val scanItems = MutableStateFlow<List<Candidate>>(emptyList())
    init { viewModelScope.launch { needsSetup.value = auth.needsSetup(); initialized.value = true } }
    fun clearMessage() { message.value = null }
    fun showMessage(text: String) { message.value = text }
    fun fail(t: Throwable) { message.value = when (t) {
        is android.database.sqlite.SQLiteConstraintException -> "IMEI یا شناسه یکتا تکراری است؛ اطلاعات را بررسی کنید"
        is SecurityException, is IllegalArgumentException, is IllegalStateException -> t.message ?: "عملیات ناموفق بود"
        else -> "عملیات انجام نشد؛ اطلاعات و فضای ذخیره‌سازی را بررسی کنید"
    } }
    fun setup(name: String, username: String, email: String, password: String) = viewModelScope.launch {
        busy.value = true
        try { auth.setup(name, username.lowercase(), email, password); needsSetup.value = false; showMessage("مدیر ایجاد شد؛ وارد شوید") }
        catch (t: Throwable) { fail(t) } finally { busy.value = false }
    }
    fun login(username: String, password: String) = viewModelScope.launch {
        busy.value = true
        try { auth.signIn(username, password) } catch (t: Throwable) { fail(t) } finally { busy.value = false }
    }
    fun newForm(kind: TradeKind) {
        editing.value = null; errors.value = emptyMap();
        draft.value = TradeDraft(kind = kind, dateIso = PersianDates.today())
    }
    fun editForm(id: String) = viewModelScope.launch {
        try {
            val row = repo.trade(id) ?: error("معامله یافت نشد")
            val t = row.trade
            require(t.deletedAt == null) { "معامله حذف شده است" }
            editing.value = row
            errors.value = emptyMap()
            draft.value = TradeDraft(row.imei1, row.imei2.orEmpty(), TradeKind.valueOf(t.kind.uppercase()), t.dateIso,
                t.firstName, t.lastName, t.nationalId, t.contact, t.model, t.color, t.memory,
                PhoneCondition.valueOf(t.condition.uppercase()), RegisterType.valueOf(t.registration.uppercase()), t.notes)
        } catch (t: Throwable) { fail(t) }
    }
    fun update(transform: (TradeDraft) -> TradeDraft) { draft.value = transform(draft.value) }
    fun save(onDone: (String) -> Unit) = viewModelScope.launch {
        val d = draft.value
        val found = TradeValidation.validate(d)
        errors.value = found
        if (found.isNotEmpty()) { showMessage("لطفاً خطاهای فرم را اصلاح کنید"); return@launch }
        busy.value = true
        try {
            val old = editing.value
            if (old == null) {
                val id = repo.create(d); message.value = "${d.kind.fa} با موفقیت ثبت شد"; onDone(id)
            } else {
                repo.edit(old.trade.id, old.trade.version, d)
                message.value = "معامله ویرایش شد"; onDone(old.trade.id)
            }
        } catch (t: Throwable) { fail(t) } finally { busy.value = false }
    }
    fun delete(id: String, restore: Boolean, done: () -> Unit) = viewModelScope.launch {
        busy.value = true
        try { repo.changeDeleted(id, restore); showMessage(if (restore) "معامله بازیابی شد" else "معامله حذف شد"); done() }
        catch (t: Throwable) { fail(t) } finally { busy.value = false }
    }
    fun scanFor(field: String) { scanTarget.value = field; scanItems.value = emptyList() }
    fun candidates(raw: String) { scanItems.value = BarcodeParser.parse(raw) }
    /** Caller makes an explicit choice in the scanner UI before any draft value changes. */
    fun useScanned(primary: String, secondary: String? = null) {
        val field = scanTarget.value
        if (field == "search") search.value = primary
        else if (field == "report") reportImei.value = primary
        else update { d -> when (field) {
            "imei2" -> d.copy(imei2 = primary)
            else -> d.copy(imei1 = primary, imei2 = secondary ?: d.imei2)
        } }
        scanItems.value = emptyList()
    }
}
