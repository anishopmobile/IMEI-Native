package ir.anishop.imei.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ir.anishop.imei.domain.BarcodeParser
import ir.anishop.imei.ui.MainViewModel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun ScannerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var message by remember { mutableStateOf("بارکد جعبه گوشی را داخل کادر بگیرید") }
    var selected by remember { mutableStateOf(0) }
    var swap by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    val hits by vm.scanItems.collectAsState()
    val worker = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val waiting = remember { AtomicBoolean(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var providerRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) { onDispose {
        providerRef?.unbindAll()
        scanner.close(); worker.shutdown(); camera = null
    } }
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF071626)).padding(14.dp)) {
        Text("اسکن بارکد IMEI", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (!granted) {
            Text("دوربین فقط هنگام اسکن فعال می‌شود؛ هیچ تصویری ذخیره یا ارسال نمی‌شود.", color = androidx.compose.ui.graphics.Color.White)
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("اجازه استفاده از دوربین") }
            OutlinedButton(onClick = onBack) { Text("ورود دستی / بازگشت") }
        } else {
            AndroidView(
                factory = { context -> PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } },
                modifier = Modifier.fillMaxWidth().weight(1f).border(3.dp, androidx.compose.ui.graphics.Color.Cyan, RoundedCornerShape(22.dp)),
                update = { previewView ->
                    if (camera == null && hits.isEmpty()) {
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            runCatching {
                                val provider = future.get()
                                providerRef = provider
                                provider.unbindAll()
                                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                analysis.setAnalyzer(worker) { proxy ->
                                    val media = proxy.image
                                    if (media == null || hits.isNotEmpty() || !waiting.compareAndSet(false, true)) { proxy.close(); return@setAnalyzer }
                                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                    scanner.process(image).addOnSuccessListener { codes ->
                                        val parsed = codes.flatMap { BarcodeParser.parse(it.rawValue.orEmpty()) }.distinctBy { it.imei }
                                        if (parsed.isNotEmpty()) {
                                            provider.unbind(analysis)
                                            vm.scanItems.value = parsed
                                        } else if (codes.isNotEmpty()) message = "IMEI معتبر پیدا نشد؛ ورود دستی امکان‌پذیر است"
                                    }.addOnCompleteListener { waiting.set(false); proxy.close() }
                                }
                                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                                camera?.cameraControl?.enableTorch(torch)
                            }.onFailure { message = "دوربین آماده نیست؛ ورود دستی را امتحان کنید" }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            Text(message, color = androidx.compose.ui.graphics.Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) }) { Text(if (torch) "خاموش کردن چراغ" else "چراغ‌قوه") }
                OutlinedButton(onClick = { vm.scanItems.value = emptyList(); camera = null; waiting.set(false); message = "دوباره اسکن کنید" }) { Text("تلاش دوباره") }
                OutlinedButton(onClick = onBack) { Text("انصراف") }
            }
        }
    }
    if (hits.isNotEmpty()) AlertDialog(
        onDismissRequest = { vm.scanItems.value = emptyList() },
        title = { Text("IMEI شناسایی شد") },
        text = {
            Column {
                Text("شناسه‌ها را بررسی و انتخاب کنید. ترتیب را بدون تأیید تغییر نمی‌دهیم.")
                hits.forEachIndexed { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == i, onClick = { selected = i })
                        Text("${c.imei} ${c.slot?.let { "(IMEI$it)" }.orEmpty()}")
                    }
                }
                if (hits.size >= 2 && vm.scanTarget.value == "imei1") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = swap, onCheckedChange = { swap = it })
                        Text("IMEI دوم را نیز در فیلد دیگر قرار بده")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val primary = hits[selected].imei
            val secondary = if (swap) hits.firstOrNull { it.imei != primary }?.imei else null
            vm.useScanned(primary, secondary); onBack()
        }) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = { vm.scanItems.value = emptyList(); selected = 0; swap = false; camera = null }) { Text("اسکن مجدد") } }
    )
}
