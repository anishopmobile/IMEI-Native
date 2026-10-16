package ir.anishop.imei.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.anishop.imei.data.TradeRow
import ir.anishop.imei.domain.*
import ir.anishop.imei.scanner.ScannerScreen
import kotlinx.coroutines.flow.map
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextAlign

private val navy = Color(0xFF0B2240)
private val blue = Color(0xFF1583EB)
private val mint = Color(0xFF77D6AE)

@Composable fun RootScreen(vm: MainViewModel) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val themeMode by vm.preferences.mode.collectAsStateWithLifecycle(initialValue = "system")
    val dark = when (themeMode) { "dark" -> true; "light" -> false; else -> systemDark }
    val colors = if (dark) darkColorScheme(primary = Color(0xFF5FACFF), secondary = mint, background = Color(0xFF071626), surface = Color(0xFF122841))
        else lightColorScheme(primary = Color(0xFF075BC9), secondary = Color(0xFF187B61), background = Color(0xFFF3F6FB), surface = Color.White)
    MaterialTheme(colorScheme = colors) {
        val ready by vm.initialized.collectAsStateWithLifecycle()
        val setup by vm.needsSetup.collectAsStateWithLifecycle()
        val user by vm.auth.loggedIn.collectAsStateWithLifecycle()
        val message by vm.message.collectAsStateWithLifecycle()
        if (message != null) AlertDialog(onDismissRequest = vm::clearMessage,
            title = { Text("پیام برنامه") }, text = { Text(message!!) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("متوجه شدم") } })
        when {
            !ready -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            setup -> AccessScreen(vm, true)
            user == null -> AccessScreen(vm, false)
            else -> AppNavigation(vm, user?.role == "admin")
        }
    }
}

@Composable private fun AccessScreen(vm: MainViewModel, setup: Boolean) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(navy).padding(25.dp), Alignment.Center) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (setup) "راه‌اندازی دفتر IMEI" else "ورود به دفتر آفلاین IMEI", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (setup) "حساب مدیر را با رمز قوی بسازید؛ اطلاعات فقط روی همین دستگاه می‌ماند." else "برای دسترسی به اطلاعات، وارد حساب محلی خود شوید.")
                if (setup) OutlinedTextField(name, { name = it }, label = { Text("نام مدیر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(username, { username = it }, label = { Text("نام کاربری انگلیسی") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                if (setup) OutlinedTextField(email, { email = it }, label = { Text("ایمیل مدیر") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("رمز عبور (حداقل ۱۰ نویسه)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { if (setup) vm.setup(name, username, email, password) else vm.login(username, password) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (setup) "ایجاد مدیر" else "ورود")
                }
                Text("بدون اینترنت • بدون ارسال اطلاعات • پایگاه داده خصوصی دستگاه", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable private fun AppNavigation(vm: MainViewModel, admin: Boolean) {
    val nav = rememberNavController()
    Scaffold(
        topBar = {
            Surface(color = navy) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("دفتر IMEI", modifier = Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Default.Search, "جستجو", tint = Color.White) }
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "تنظیمات", tint = Color.White) }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                listOf(Triple("home", "خانه", Icons.Default.Home), Triple("search", "دستگاه‌ها", Icons.Default.Smartphone), Triple("reports", "گزارش‌ها", Icons.Default.BarChart), Triple("settings", "تنظیمات", Icons.Default.Settings)).forEach { (route, title, icon) ->
                    NavigationBarItem(selected = false, onClick = { nav.navigate(route) { launchSingleTop = true; popUpTo("home") } }, icon = { Icon(icon, title) }, label = { Text(title) })
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize().padding(padding)) {
            composable("home") { Dashboard(vm, open = { nav.navigate(it) }) }
            composable("search") { SearchScreen(vm, open = { nav.navigate(it) }) }
            composable("reports") { ReportsScreen(vm, open = { nav.navigate(it) }) }
            composable("form/{kind}", arguments = listOf(navArgument("kind") { type = NavType.StringType })) { back ->
                val kind = if (back.arguments?.getString("kind") == "sell") TradeKind.SELL else TradeKind.BUY
                var initialized by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(kind) { if (!initialized) { vm.newForm(kind); initialized = true } }
                TradeForm(vm, onScan = { target -> vm.scanFor(target); nav.navigate("scanner") }, onSaved = { nav.navigate("trade/$it") { popUpTo("home") } })
            }
            composable("edit/{id}") { back ->
                val id = back.arguments?.getString("id").orEmpty()
                var initialized by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(id) { if (!initialized) { vm.editForm(id); initialized = true } }
                TradeForm(vm, onScan = { target -> vm.scanFor(target); nav.navigate("scanner") }, onSaved = { nav.navigate("trade/$it") { popUpTo("home") } })
            }
            composable("scanner") { ScannerScreen(vm, onBack = { nav.popBackStack() }) }
            composable("trade/{id}") { back -> TradeDetails(vm, back.arguments?.getString("id").orEmpty(), admin, open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
            composable("person/{id}") { back -> PersonHistory(vm, back.arguments?.getString("id").orEmpty(), open = { nav.navigate(it) }) }
            composable("phone/{id}") { back -> PhoneDetails(vm, back.arguments?.getString("id").orEmpty(), open = { nav.navigate(it) }) }
            composable("settings") { SettingsScreen(vm, admin, open = { nav.navigate(it) }) }
            composable("users") { if (admin) UsersScreen(vm) else Text("این بخش فقط برای مدیر است") }
            composable("deleted") { DeletedScreen(vm, open = { nav.navigate(it) }) }
        }
    }
}

@Composable private fun Dashboard(vm: MainViewModel, open: (String) -> Unit) {
    val phones by vm.repo.phoneCount.collectAsStateWithLifecycle(initialValue = 0)
    var today by remember { mutableStateOf(PersianDates.today()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); today = PersianDates.today() } }
    val buys by vm.repo.dayCount(TradeKind.BUY, today).collectAsStateWithLifecycle(initialValue = 0)
    val sells by vm.repo.dayCount(TradeKind.SELL, today).collectAsStateWithLifecycle(initialValue = 0)
    val trades by vm.repo.trades.collectAsStateWithLifecycle(initialValue = emptyList())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(15.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Text("خلاصه فعالیت · ${PersianDates.display(today)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("دستگاه‌ها", Rules.displayDigits(phones.toString()), Modifier.weight(1f))
                StatCard("خرید امروز", Rules.displayDigits(buys.toString()), Modifier.weight(1f))
                StatCard("فروش امروز", Rules.displayDigits(sells.toString()), Modifier.weight(1f))
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { open("form/buy") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ShoppingCart, null); Text(" خرید جدید") }
            Button(onClick = { open("form/sell") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Sell, null); Text(" فروش جدید") }
        } }
        item { OutlinedButton(onClick = { open("search") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Text(" جستجو یا اسکن IMEI") } }
        item { Text("تراکنش‌های اخیر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (trades.isEmpty()) item { EmptyHint("هنوز معامله‌ای ثبت نشده است") }
        items(trades.take(10), key = { it.trade.id }) { TradeCard(it) { open("trade/${it.trade.id}") } }
    }
}
@Composable private fun StatCard(label: String, count: String, modifier: Modifier) { ElevatedCard(modifier) {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(count, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
} }
@Composable private fun EmptyHint(label: String) { Card { Text(label, modifier = Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center) } }
@Composable private fun TradeCard(row: TradeRow, onClick: () -> Unit) {
    val t = row.trade
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.model, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                AssistChip(onClick = onClick, label = { Text(if (t.kind == "buy") "خرید" else "فروش") })
            }
            Text("IMEI: ${row.imei1}", style = MaterialTheme.typography.bodySmall)
            Text("${t.firstName} ${t.lastName}  •  ${PersianDates.display(t.dateIso)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun SearchScreen(vm: MainViewModel, open: (String) -> Unit) {
    val phones by vm.repo.phones.collectAsStateWithLifecycle(initialValue = emptyList())
    val query by vm.search.collectAsStateWithLifecycle()
    val filtered = remember(phones, query) { phones.filter { query.isBlank() || it.imei1.contains(Rules.digits(query)) || it.imei2.orEmpty().contains(Rules.digits(query)) } }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("جستجو و مدیریت دستگاه‌ها", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { vm.search.value = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("IMEI اول یا دوم") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            IconButton(onClick = { vm.scanFor("search"); open("scanner") }) { Icon(Icons.Default.QrCodeScanner, "اسکن IMEI") }
        }
        Text("${filtered.size} دستگاه", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.phoneId }) { p -> ElevatedCard(onClick = { open("phone/${p.phoneId}") }) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(p.lastModel ?: "دستگاه بدون معامله", style = MaterialTheme.typography.titleMedium)
                    Text("IMEI 1: ${p.imei1}")
                    Text("IMEI 2: ${p.imei2 ?: "ثبت نشده"}")
                    Text("${p.tradeCount} معامله • ${if (p.lastKind == "buy") "آخرین معامله: خرید" else if (p.lastKind == "sell") "آخرین معامله: فروش" else "بدون سابقه"}", color = MaterialTheme.colorScheme.primary)
                }
            } }
            if (filtered.isEmpty()) item { EmptyHint("دستگاهی با این شناسه پیدا نشد") }
        }
    }
}

@Composable private fun ReportsScreen(vm: MainViewModel, open: (String) -> Unit) {
    val trades by vm.repo.trades.collectAsStateWithLifecycle(initialValue = emptyList())
    var kind by remember { mutableStateOf("all") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    val imei by vm.reportImei.collectAsStateWithLifecycle()
    val filtered = trades.filter { t -> (kind == "all" || t.trade.kind == kind) && (from.isBlank() || t.trade.dateIso >= from) && (to.isBlank() || t.trade.dateIso <= to) &&
        (imei.isBlank() || t.imei1.contains(Rules.digits(imei)) || t.imei2.orEmpty().contains(Rules.digits(imei))) }
    var dateTarget by remember { mutableStateOf<String?>(null) }
    if (dateTarget != null) PersianDateDialog(if (dateTarget == "from") from.ifBlank { PersianDates.today() } else to.ifBlank { PersianDates.today() }, {
        if (dateTarget == "from") from = it else to = it
        dateTarget = null
    }, { dateTarget = null })
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("گزارش معاملات", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "همه", "buy" to "خرید", "sell" to "فروش").forEach { (v, name) -> FilterChip(selected = kind == v, onClick = { kind = v }, label = { Text(name) }) }
        }
        Row {
            OutlinedButton(onClick = { dateTarget = "from" }) { Text("از: ${if (from.isBlank()) "همه" else PersianDates.display(from)}") }
            OutlinedButton(onClick = { dateTarget = "to" }) { Text("تا: ${if (to.isBlank()) "همه" else PersianDates.display(to)}") }
            TextButton(onClick = { from = ""; to = "" }) { Text("پاک‌کردن") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(imei, { vm.reportImei.value = it }, label = { Text("فیلتر IMEI") }, singleLine = true, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.scanFor("report"); open("scanner") }) { Icon(Icons.Default.QrCodeScanner, "اسکن شناسه فیلتر") }
        }
        Text("${filtered.size} معامله", modifier = Modifier.padding(9.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.trade.id }) { row -> TradeCard(row) { open("trade/${row.trade.id}") } }
        }
    }
}
