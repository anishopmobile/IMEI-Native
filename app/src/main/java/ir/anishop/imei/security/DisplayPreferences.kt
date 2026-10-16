package ir.anishop.imei.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("ui_settings")

class DisplayPreferences(private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    /** system, dark, light; defaults to system if unset. */
    val mode: Flow<String> = context.settingsStore.data.map { it[theme] ?: "system" }
    suspend fun setMode(value: String) {
        require(value in listOf("system", "dark", "light"))
        context.settingsStore.edit { it[theme] = value }
    }
}
