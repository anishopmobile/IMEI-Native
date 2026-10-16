package ir.anishop.imei.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.anishop.imei.backup.PortableBackup
import ir.anishop.imei.backup.LegacyJsonImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable fun SettingsScreen(vm: MainViewModel, admin: Boolean, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = remember { PortableBackup(vm.database, vm.auth, vm.vault) }
    val legacyImporter = remember { LegacyJsonImporter(vm.database, vm.auth, vm.vault) }
    val theme by vm.preferences.mode.collectAsStateWithLifecycle(initialValue = "system")
    var password by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var legacyPending by remember { mutableStateOf<ByteArray?>(null) }
    var legacyPreview by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val bytes = withContext(Dispatchers.IO) { backup.export(password.toCharArray()) }
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("دسترسی به فایل برقرار نیست") }
                vm.showMessage("فایل پشتیبان رمزنگاری‌شده ذخیره شد")
            } catch (t: Throwable) { vm.fail(t) }
            finally { password = ""; busy = false }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val stream = ctx.contentResolver.openInputStream(uri) ?: error("فایل خوانده نشد")
                    stream.use { input ->
                        val out = ByteArrayOutputStream(); val buf = ByteArray(8192); var count = 0
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            count += n; require(count <= 15_000_100) { "فایل بیش از حد بزرگ است" }
                            out.write(buf, 0, n)
                        }
                        out.toByteArray()
                    }
                }
                val text = withContext(Dispatchers.IO) { backup.preview(bytes, password.toCharArray()) }
                pending = bytes; preview = text
            } catch (_: Exception) { vm.showMessage("رمز فایل، نسخه یا ساختار پشتیبان معتبر نیست") }
            finally { busy = false }
        }
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val bytes = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val out = ByteArrayOutputStream(); val buf = ByteArray(8192); var size = 0
                        while (true) { val n = input.read(buf); if (n < 0) break; size += n
                            require(size <= 15_000_000) { "فایل بزرگ است" }; out.write(buf, 0, n) }
                        out.toByteArray()
                    } ?: error("فایل باز نشد")
                }
                legacyPreview = withContext(Dispatchers.IO) { legacyImporter.preview(bytes) }
                legacyPending = bytes
            } catch (_: Exception) { vm.showMessage("پشتیبان قدیمی معتبر نیست یا برخی اطلاعات با قواعد جدید ناسازگار است") }
            finally { busy = false }
        }
    }
    if (legacyPreview != null && legacyPending != null) AlertDialog(onDismissRequest = { legacyPreview = null; legacyPending = null },
        title = { Text("تأیید ورود سوابق قدیمی") }, text = { Text(legacyPreview!!) },
        confirmButton = { TextButton(onClick = {
            val bytes = legacyPending!!; legacyPreview = null; legacyPending = null
            scope.launch { busy = true
                try { val result = withContext(Dispatchers.IO) { legacyImporter.importLegacy(bytes) }
                    vm.showMessage("${result.imported} معامله وارد شد؛ ${result.skipped} تکراری رد شد")
                } catch (_: Exception) { vm.showMessage("انتقال متوقف شد؛ داده‌های قبلی بدون تغییر باقی ماندند. IMEI یا کد ملی داده‌های قدیمی را بررسی کنید") }
                finally { busy = false }
            }
        }) { Text("واردکردن سوابق") } },
        dismissButton = { TextButton(onClick = { legacyPreview = null; legacyPending = null }) { Text("لغو") } })
    if (preview != null && pending != null) AlertDialog(
        onDismissRequest = { pending = null; preview = null },
        title = { Text("تأیید بازیابی اطلاعات") },
        text = { Text(preview!! + "\n\nاین عملیات جایگزین داده‌های فعلی خواهد شد. از اطلاعات موجود نسخه پشتیبان دارید؟") },
        confirmButton = { TextButton(onClick = {
            val bytes = pending!!; pending = null; preview = null
            scope.launch {
                busy = true
                try { withContext(Dispatchers.IO) { backup.restore(bytes, password.toCharArray()) }; vm.showMessage("بازیابی به طور کامل انجام شد") }
                catch (_: Exception) { vm.showMessage("بازیابی انجام نشد؛ داده‌های قبلی بدون تغییر باقی ماندند") }
                finally { password = ""; busy = false }
            }
        }) { Text("تأیید و جایگزینی") } },
        dismissButton = { TextButton(onClick = { pending = null; preview = null }) { Text("لغو") } }
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("پشتیبان‌گیری و تنظیمات", style = MaterialTheme.typography.headlineSmall) }
        if (admin) {
            item { ElevatedCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پشتیبان رمزنگاری‌شده آفلاین", style = MaterialTheme.typography.titleMedium)
                    Text("رمز حداقل ۱۲ نویسه وارد کنید؛ برای انتقال به دستگاه دیگر آن را به خاطر بسپارید. با گم‌شدن رمز، فایل قابل بازیابی نیست.")
                    OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("رمز پشتیبان") }, visualTransformation = PasswordVisualTransformation())
                    Button(onClick = { if (password.length >= 12) saveLauncher.launch("imei-backup.imeibak") else vm.showMessage("رمز پشتیبان حداقل ۱۲ نویسه باشد") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Text(" تهیه پشتیبان امن") }
                    OutlinedButton(onClick = { if (password.isNotEmpty()) openLauncher.launch(arrayOf("*/*")) else vm.showMessage("ابتدا رمز فایل پشتیبان را وارد کنید") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Upload, null); Text(" بازیابی از فایل") }
                    Text("بازیابی فقط با تأیید شما انجام می‌شود. این نسخه شامل معاملات، دستگاه‌ها و سوابق است؛ حساب‌های محلی انتقال نمی‌یابند.", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("انتقال از پروژه اصلی (فایل JSON)", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { legacyLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("انتخاب فایل خروجی نسخه وب") }
                    Text("فقط فایل خروجی دارای transactions پشتیبانی می‌شود؛ داده نامعتبر باعث لغو کامل ورود خواهد شد.")
                }
            } }
            item { ElevatedCard {
                Column(Modifier.padding(14.dp)) {
                    Text("مدیریت و دسترسی", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { open("users") }) { Text("مدیریت کاربران محلی") }
                    TextButton(onClick = { open("deleted") }) { Text("بازیابی معاملات حذف‌شده") }
                }
            } }
        }
        item { ElevatedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ظاهر برنامه", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to "سیستم", "light" to "روشن", "dark" to "تیره").forEach { (value, label) ->
                    FilterChip(selected = theme == value, onClick = { scope.launch { vm.preferences.setMode(value) } }, label = { Text(label) })
                }
            }
        } } }
        item { ElevatedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("حریم خصوصی", style = MaterialTheme.typography.titleMedium)
            Text("این نسخه به اینترنت نیاز ندارد و تنها مجوز لازم، دوربین برای خواندن IMEI است.")
            Text("داده‌ها در فضای خصوصی همین دستگاه ذخیره می‌شوند. به دلیل قفل امنیتی، تصویر صفحات و سابقه برنامه در نمای برنامه‌های اخیر مخفی می‌شود.")
            Text("نسخه توسعه ۰.۱.۰؛ انتشار عمومی نیازمند آزمون روی دستگاه واقعی است.")
        } } }
        item { OutlinedButton(onClick = vm.auth::logout, modifier = Modifier.fillMaxWidth()) { Text("قفل کردن و خروج") } }
    }
}

@Composable fun UsersScreen(vm: MainViewModel) {
    val users by vm.repo.users.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resetUser by remember { mutableStateOf<String?>(null) }
    if (resetUser != null) AlertDialog(onDismissRequest = { resetUser = null }, title = { Text("تغییر رمز همکار") },
        text = { OutlinedTextField(password, { password = it }, label = { Text("رمز جدید") }, visualTransformation = PasswordVisualTransformation()) },
        confirmButton = { TextButton(onClick = {
            val id = resetUser!!; resetUser = null
            scope.launch { try { vm.auth.resetPassword(id, password); vm.showMessage("رمز تغییر کرد") } catch (e: Exception) { vm.fail(e) }; password = "" }
        }) { Text("تغییر رمز") } },
        dismissButton = { TextButton(onClick = { resetUser = null }) { Text("انصراف") } })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("کاربران محلی", style = MaterialTheme.typography.headlineSmall)
        ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("افزودن همکار")
            OutlinedTextField(name, { name = it }, label = { Text("نام") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("نام کاربری انگلیسی") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("ایمیل همکار") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("رمز حداقل ۱۰ نویسه") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
            Button(onClick = {
                scope.launch {
                    try { vm.auth.createStaff(name, username, email, password); name = ""; username = ""; email = ""; password = ""; vm.showMessage("همکار افزوده شد") }
                    catch (e: Throwable) { vm.fail(e) }
                }
            }) { Text("افزودن همکار") }
        } }
        users.forEach { u -> ElevatedCard {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("${u.name} • ${u.username}")
                    Text(if (u.role == "admin") "مدیر" else "همکار", style = MaterialTheme.typography.labelMedium)
                }
                if (u.role != "admin") {
                    Column {
                        Switch(u.active, onCheckedChange = { active -> scope.launch { try { vm.auth.setActive(u.id, active) } catch (e: Exception) { vm.fail(e) } } })
                        TextButton(onClick = { password = ""; resetUser = u.id }) { Text("تغییر رمز") }
                    }
                }
            }
        } }
    }
}
