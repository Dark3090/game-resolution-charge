package com.game.reschange

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * Gerencia as preferencias de escala por app.
 * Alem do SharedPreferences (para a UI), grava um arquivo
 * world-readable em /data/local/tmp/reschange_config.txt
 * para o XposedInit ler de qualquer processo sem SELinux block.
 *
 * Formato do arquivo: uma linha por app
 *   com.exemplo.app=0.80
 */
object ResChangePrefs {
    private const val PREF_NAME   = "scale_prefs"
    const val CONFIG_FILE         = "/data/local/tmp/reschange_config.txt"

    fun saveScale(context: Context, packageName: String, scale: Float) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putFloat(packageName, scale) }
        writeConfigFile(context)
    }

    fun getScale(context: Context, packageName: String): Float {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getFloat(packageName, 1.0f)
    }

    fun getAllPackages(context: Context): Set<String> {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).all.keys
    }

    fun removeScale(context: Context, packageName: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { remove(packageName) }
        writeConfigFile(context)
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
        writeConfigFile(context)
    }

    /**
     * Grava /data/local/tmp/reschange_config.txt com chmod 644.
     * Legivel por qualquer processo (incluindo system_server)
     * sem depender de createPackageContext (bloqueado pelo SELinux 12+).
     */
    private fun writeConfigFile(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lines = prefs.all
                .filterValues { it is Float && (it as Float) < 1.0f }
                .map { (pkg, scale) -> "$pkg=$scale" }
                .joinToString("\n")
            File(CONFIG_FILE).writeText(lines)
            Runtime.getRuntime()
                .exec(arrayOf("su", "-c", "chmod 644 $CONFIG_FILE"))
                .waitFor()
        } catch (_: Exception) {}
    }
}
