package ir.anishop.imei.ui

import android.widget.NumberPicker
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.anishop.imei.domain.*

@Composable fun TradeForm(vm: MainViewModel, onScan: (String) -> Unit, onSaved: (String) -> Unit) {
    val d by vm.draft.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    var chooseDate by remember { mutableStateOf(false) }
    if (chooseDate) PersianDateDialog(d.dateIso, { date -> vm.update { it.copy(dateIso = date) }; chooseDate = false }, { chooseDate = false })
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (editing == null) "ثبت ${d.kind.fa} گوشی" else "ویرایش ${d.kind.fa}", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.update { it.copy(kind = TradeKind.BUY) } }, enabled = editing == null, colors = ButtonDefaults.buttonColors(containerColor = if (d.kind == TradeKind.BUY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.weight(1f)) { Text("خرید گوشی") }
                Button(onClick = { vm.update { it.copy(kind = TradeKind.SELL) } }, enabled = editing == null, colors = ButtonDefaults.buttonColors(containerColor = if (d.kind == TradeKind.SELL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.weight(1f)) { Text("فروش گوشی") }
            }
            FormSection("مشخصات گوشی") {
                ImeiInput("IMEI اول *", d.imei1, errors["imei1"], enabled = editing == null, onChange = { vm.update { d -> d.copy(imei1 = it) } }, onScan = { onScan("imei1") })
                ImeiInput("IMEI دوم (اختیاری)", d.imei2, errors["imei2"], enabled = editing == null, onChange = { vm.update { d -> d.copy(imei2 = it) } }, onScan = { onScan("imei2") })
                Field("مدل گوشی *", d.model, errors["model"]) { vm.update { d -> d.copy(model = it) } }
                Field("رنگ *", d.color, errors["color"]) { vm.update { d -> d.copy(color = it) } }
                Field("حافظه (مثل ۲۵۶ گیگابایت) *", d.memory, errors["memory"]) { vm.update { d -> d.copy(memory = it) } }
                Text("وضعیت گوشی *", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhoneCondition.entries.forEach { item -> FilterChip(selected = d.condition == item, onClick = { vm.update { it.copy(condition = item) } }, label = { Text(item.fa) }) }
                }
                ErrorText(errors["condition"])
                Text("وضعیت رجیستر *", style = MaterialTheme.typography.titleSmall)
                Column {
                    RegisterType.entries.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.update { it.copy(registration = item) } }) {
                            RadioButton(selected = d.registration == item, onClick = { vm.update { it.copy(registration = item) } })
                            Text(item.fa)
                        }
                    }
                }
                ErrorText(errors["registration"])
                OutlinedButton(onClick = { chooseDate = true }, modifier = Modifier.fillMaxWidth()) { Text("تاریخ معامله (شمسی) * : ${PersianDates.display(d.dateIso)}") }
                ErrorText(errors["dateIso"])
            }
            FormSection("طرف مقابل معامله") {
                Field("کد ملی *", d.nationalId, errors["nationalId"], KeyboardType.Number) { vm.update { d -> d.copy(nationalId = it) } }
                Field("شماره تماس (اختیاری)", d.contact, errors["contact"], KeyboardType.Phone) { vm.update { d -> d.copy(contact = it) } }
                Field("نام *", d.firstName, errors["firstName"]) { vm.update { d -> d.copy(firstName = it) } }
                Field("نام خانوادگی *", d.lastName, errors["lastName"]) { vm.update { d -> d.copy(lastName = it) } }
            }
            FormSection("یادداشت معامله") {
                OutlinedTextField(d.notes, { vm.update { old -> old.copy(notes = it.take(5001)) } }, label = { Text("توضیحات (اختیاری)") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                ErrorText(errors["notes"])
            }
        }
        Surface(shadowElevation = 12.dp) {
            Button(onClick = { vm.save(onSaved) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 52.dp)) {
                Text(if (editing == null) "ثبت ${d.kind.fa}" else "ذخیره ویرایش")
            }
        }
    }
}

@Composable private fun FormSection(title: String, body: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            body()
        }
    }
}
@Composable private fun ErrorText(error: String?) { if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
@Composable private fun Field(label: String, value: String, error: String?, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        isError = error != null, supportingText = if (error != null) ({ Text(error) }) else null,
        singleLine = type != KeyboardType.Text, keyboardOptions = KeyboardOptions(keyboardType = type),
        textStyle = if (type == KeyboardType.Number || type == KeyboardType.Phone) TextStyle(textDirection = TextDirection.Ltr) else TextStyle.Default)
}
@Composable private fun ImeiInput(label: String, value: String, error: String?, enabled: Boolean, onChange: (String) -> Unit, onScan: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value, onChange, enabled = enabled, label = { Text(label) }, isError = error != null,
            supportingText = if (error != null) ({ Text(error) }) else null,
            modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(textDirection = TextDirection.Ltr))
        IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, "اسکن $label") }
    }
}

/** Persian native wheel picker; dynamically validated via Android's ICU Persian calendar. */
@Composable fun PersianDateDialog(initialIso: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val (initialYear, initialMonth, initialDay) = remember(initialIso) { PersianDates.toPersian(initialIso.ifBlank { PersianDates.today() }) }
    var year by remember(initialIso) { mutableIntStateOf(initialYear) }
    var month by remember(initialIso) { mutableIntStateOf(initialMonth) }
    var day by remember(initialIso) { mutableIntStateOf(initialDay) }
    var warning by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("انتخاب تاریخ شمسی") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AndroidView(factory = { context -> NumberPicker(context).apply { minValue = 1; maxValue = 31; value = day; setOnValueChangedListener { _, _, v -> day = v } } }, modifier = Modifier.weight(1f))
                    AndroidView(factory = { context -> NumberPicker(context).apply { minValue = 1; maxValue = 12; value = month; displayedValues = PersianDates.months.toTypedArray(); setOnValueChangedListener { _, _, v -> month = v } } }, modifier = Modifier.weight(1.3f))
                    AndroidView(factory = { context -> NumberPicker(context).apply { minValue = 1300; maxValue = 1500; value = year; setOnValueChangedListener { _, _, v -> year = v } } }, modifier = Modifier.weight(1f))
                }
                ErrorText(warning)
            }
        }, confirmButton = { TextButton(onClick = {
            try { onConfirm(PersianDates.fromPersian(year, month, day)) }
            catch (_: IllegalArgumentException) { warning = "این روز در ماه انتخاب‌شده وجود ندارد" }
        }) { Text("انتخاب") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}
