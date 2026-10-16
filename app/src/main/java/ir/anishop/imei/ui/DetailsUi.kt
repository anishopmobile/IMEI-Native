package ir.anishop.imei.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.anishop.imei.data.TradeRow
import ir.anishop.imei.domain.PersianDates
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable fun TradeDetails(vm: MainViewModel, tradeId: String, admin: Boolean, open: (String) -> Unit, onBack: () -> Unit) {
    var trade by remember(tradeId) { mutableStateOf<TradeRow?>(null) }
    val audits by vm.repo.audits(tradeId).collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(tradeId) { trade = vm.repo.trade(tradeId) }
    val row = trade
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("حذف منطقی معامله") },
        text = { Text("معامله از فهرست فعال حذف می‌شود اما سابقه آن حفظ خواهد شد. ادامه می‌دهید؟") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(tradeId, false, onBack) }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } })
    if (row == null) { CircularProgressIndicator(); return }
    val t = row.trade
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("جزئیات ${if (t.kind == "buy") "خرید" else "فروش"}", style = MaterialTheme.typography.headlineSmall) }
        item { ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t.model, style = MaterialTheme.typography.titleLarge)
                Text("IMEI1: ${row.imei1}")
                Text("IMEI2: ${row.imei2 ?: "ثبت نشده"}")
                Text("${t.color} • ${t.memory} • ${if (t.condition == "new") "آکبند" else "کارکرده"}")
                Text("رجیستر: ${when(t.registration) { "company" -> "شرکتی"; "passenger" -> "مسافری"; else -> "بدون رجیستر" }}")
                Text("تاریخ معامله: ${PersianDates.display(t.dateIso)}")
                Text("نام: ${t.firstName} ${t.lastName}")
                Text("کد ملی: ${t.nationalId}")
                Text("تماس: ${t.contact.ifBlank { "ثبت نشده" }}")
                Text("توضیحات: ${t.notes.ifBlank { "—" }}")
                Text("ویرایش: ${t.updatedName} / نسخه ${t.version}")
            }
        } }
        item { OutlinedButton(onClick = { open("person/${t.nationalId}") }) { Text("مشاهده سوابق این شخص") } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (t.deletedAt == null) OutlinedButton(onClick = { open("edit/${t.id}") }) { Text("ویرایش") }
            OutlinedButton(onClick = { open("phone/${t.phoneId}") }) { Text("سوابق دستگاه") }
            if (admin && t.deletedAt == null) TextButton(onClick = { confirmDelete = true }) { Text("حذف") }
            if (admin && t.deletedAt != null) Button(onClick = { vm.delete(tradeId, true) { open("trade/$tradeId") } }) { Text("بازیابی") }
        } }
        item { Text("تاریخچه تغییرات", style = MaterialTheme.typography.titleMedium) }
        items(audits, key = { it.id }) { item -> OutlinedCard {
            var expanded by remember(item.id) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("${when(item.action) { "create" -> "ایجاد"; "edit" -> "ویرایش"; "delete" -> "حذف"; "import" -> "انتقال"; else -> "بازیابی" }} توسط ${item.actorName}")
                Text(displayTime(item.at), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "بستن نسخه ثبت‌شده" else "مشاهده نسخه ثبت‌شده") }
                if (expanded) {
                    val snapshot = runCatching { org.json.JSONObject(vm.vault.open(item.snapshot)).toString(2) }.getOrDefault("نسخه تاریخچه قابل بررسی نیست")
                    Text(snapshot, style = MaterialTheme.typography.bodySmall)
                }
            }
        } }
    }
}
fun displayTime(epochMillis: Long): String = try {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    PersianDates.display(dateTime.toLocalDate().toString()) + "  " + "%02d:%02d".format(dateTime.hour, dateTime.minute)
} catch (_: Exception) { "—" }

@Composable fun PhoneDetails(vm: MainViewModel, phoneId: String, open: (String) -> Unit) {
    val rows by vm.repo.trades.collectAsStateWithLifecycle(initialValue = emptyList())
    val isAdmin = vm.auth.loggedIn.value?.role == "admin"
    val deleted by (if (isAdmin) vm.repo.deleted else kotlinx.coroutines.flow.flowOf(emptyList<TradeRow>())).collectAsStateWithLifecycle(initialValue = emptyList())
    var phone by remember(phoneId) { mutableStateOf<ir.anishop.imei.data.PhoneRow?>(null) }
    LaunchedEffect(phoneId) { phone = vm.repo.phone(phoneId) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("مشخصات و سابقه دستگاه", style = MaterialTheme.typography.headlineSmall)
        phone?.let {
            Text("IMEI اول: ${it.imei1}")
            Text("IMEI دوم: ${it.imei2 ?: "ثبت نشده"}")
        }
        Text("تمام معاملات ثبت‌شده این دستگاه", modifier = Modifier.padding(vertical = 14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items((rows + deleted).filter { it.trade.phoneId == phoneId }.sortedByDescending { it.trade.createdAt }) { t ->
                ElevatedCard(onClick = { open("trade/${t.trade.id}") }) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${if (t.trade.kind == "buy") "خرید" else "فروش"}  •  ${t.trade.model}")
                        Text("${PersianDates.display(t.trade.dateIso)} / ${t.trade.firstName} ${t.trade.lastName}")
                        if (t.trade.deletedAt != null) Text("حذف‌شده", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable fun DeletedScreen(vm: MainViewModel, open: (String) -> Unit) {
    if (vm.auth.loggedIn.value?.role != "admin") { Text("دسترسی به این بخش فقط برای مدیر مجاز است"); return }
    val rows by vm.repo.deleted.collectAsStateWithLifecycle(initialValue = emptyList())
    LazyColumn(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("معاملات حذف‌شده (فقط مدیر)", style = MaterialTheme.typography.headlineSmall) }
        if (rows.isEmpty()) item { Text("هیچ معامله حذف‌شده‌ای وجود ندارد") }
        items(rows, key = { it.trade.id }) { row -> ElevatedCard(onClick = { open("trade/${row.trade.id}") }) {
            Column(Modifier.padding(14.dp)) {
                Text("${row.trade.model} / ${row.imei1}")
                Text("${PersianDates.display(row.trade.dateIso)} • مشاهده و بازیابی")
            }
        } }
    }
}

@Composable fun PersonHistory(vm: MainViewModel, nationalId: String, open: (String) -> Unit) {
    val trades by vm.repo.trades.collectAsStateWithLifecycle(initialValue = emptyList())
    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("سوابق معاملات شخص", style = MaterialTheme.typography.headlineSmall) }
        item { Text("کد ملی: $nationalId") }
        val related = trades.filter { it.trade.nationalId == nationalId }
        if (related.isEmpty()) item { Text("معامله فعالی با این کد ملی پیدا نشد") }
        items(related, key = { it.trade.id }) { row -> ElevatedCard(onClick = { open("trade/${row.trade.id}") }) {
            Column(Modifier.padding(14.dp)) {
                Text("${row.trade.model} • ${if (row.trade.kind == "buy") "خرید" else "فروش"}")
                Text("${PersianDates.display(row.trade.dateIso)} / IMEI: ${row.imei1}")
            }
        } }
    }
}
