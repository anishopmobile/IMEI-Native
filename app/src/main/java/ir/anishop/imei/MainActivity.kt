package ir.anishop.imei

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.anishop.imei.ui.MainViewModel
import ir.anishop.imei.ui.RootScreen

class MainActivity : ComponentActivity() {
    private val root: ImeiApplication get() = application as ImeiApplication
    override fun onStop() {
        root.lastBackgroundAt = android.os.SystemClock.elapsedRealtime()
        super.onStop()
    }
    override fun onStart() {
        super.onStart()
        val since = root.lastBackgroundAt
        if (since != 0L && android.os.SystemClock.elapsedRealtime() - since >= 5 * 60_000L) root.auth.logout()
        root.lastBackgroundAt = 0L
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                RootScreen(viewModel())
            }
        }
    }
}
