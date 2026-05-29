package com.game.reschange

import android.content.Context

object ResChangePrefs {
    private const val PREFS_NAME = "reschange_scales"
    private const val PREFS_FLAGS = "reschange_flags"

    fun getScale(ctx: Context, pkg: String): Float =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(pkg, 1.0f)

    fun saveScale(ctx: Context, pkg: String, scale: Float) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(pkg, scale).apply()

    fun removeScale(ctx: Context, pkg: String) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(pkg).apply()

    fun getAllPackages(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .all.keys.toSet()

    fun clearAll(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()

    // ── Flags de performance ──────────────────────────────────────
    fun getFlags(ctx: Context, pkg: String): PerformanceFlags {
        val p = ctx.getSharedPreferences(PREFS_FLAGS, Context.MODE_PRIVATE)
        return PerformanceFlags(
            fps          = p.getString("${pkg}_fps", "Padrão") ?: "Padrão",
            perfMode     = p.getString("${pkg}_perfMode", "0") ?: "0",
            loadingBoost = p.getBoolean("${pkg}_loadingBoost", false),
            angle        = p.getBoolean("${pkg}_angle", false)
        )
    }

    fun saveFlags(ctx: Context, pkg: String, flags: PerformanceFlags) {
        ctx.getSharedPreferences(PREFS_FLAGS, Context.MODE_PRIVATE).edit()
            .putString("${pkg}_fps", flags.fps)
            .putString("${pkg}_perfMode", flags.perfMode)
            .putBoolean("${pkg}_loadingBoost", flags.loadingBoost)
            .putBoolean("${pkg}_angle", flags.angle)
            .apply()
    }

    fun removeFlags(ctx: Context, pkg: String) {
        ctx.getSharedPreferences(PREFS_FLAGS, Context.MODE_PRIVATE).edit()
            .remove("${pkg}_fps").remove("${pkg}_perfMode")
            .remove("${pkg}_loadingBoost").remove("${pkg}_angle")
            .apply()
    }
}
