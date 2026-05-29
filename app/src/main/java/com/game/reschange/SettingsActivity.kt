package com.game.reschange

import android.graphics.Color
import android.os.Bundle
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.card.MaterialCardView

class SettingsActivity : AppCompatActivity() {

    private lateinit var cardDefault: MaterialCardView
    private lateinit var cardAlternative: MaterialCardView
    private lateinit var radioDefault: RadioButton
    private lateinit var radioAlternative: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.settingsToolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Operation Mode"

        cardDefault      = findViewById(R.id.cardDefault)
        cardAlternative  = findViewById(R.id.cardAlternative)
        radioDefault     = findViewById(R.id.radioDefault)
        radioAlternative = findViewById(R.id.radioAlternative)

        applySelection(ModePrefs.getMode(this))

        cardDefault.setOnClickListener     { selectMode(ModePrefs.MODE_DEFAULT) }
        cardAlternative.setOnClickListener { selectMode(ModePrefs.MODE_ALTERNATIVE) }
        radioDefault.setOnClickListener    { selectMode(ModePrefs.MODE_DEFAULT) }
        radioAlternative.setOnClickListener{ selectMode(ModePrefs.MODE_ALTERNATIVE) }
    }

    private fun selectMode(mode: String) {
        ModePrefs.saveMode(this, mode)
        writeModeFile(mode)
        applySelection(mode)
        if (mode == ModePrefs.MODE_ALTERNATIVE) {
            runAsRoot("device_config set_sync_disabled_for_tests persistent")
            Toast.makeText(this, "Alternative Mode enabled.\nGMS sync disabled.", Toast.LENGTH_LONG).show()
        } else {
            runAsRoot("device_config set_sync_disabled_for_tests none")
            Toast.makeText(this, "Default Mode enabled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applySelection(mode: String) {
        val isAlt = mode == ModePrefs.MODE_ALTERNATIVE
        radioDefault.isChecked     = !isAlt
        radioAlternative.isChecked = isAlt
        cardDefault.setCardBackgroundColor(Color.parseColor(if (!isAlt) "#FF003730" else "#FF141414"))
        cardDefault.strokeColor = Color.parseColor(if (!isAlt) "#FF00E5CC" else "#FF303030")
        cardDefault.strokeWidth = if (!isAlt) 4 else 3
        cardAlternative.setCardBackgroundColor(Color.parseColor(if (isAlt) "#FF003730" else "#FF141414"))
        cardAlternative.strokeColor = Color.parseColor(if (isAlt) "#FF00E5CC" else "#FF303030")
        cardAlternative.strokeWidth = if (isAlt) 4 else 3
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed(); return true
    }

    private fun writeModeFile(mode: String) {
        try { val f = java.io.File("/data/local/tmp/reschange_mode.txt"); f.writeText(mode)
            runAsRoot("chmod 644 /data/local/tmp/reschange_mode.txt") } catch (_: Exception) {}
    }

    private fun runAsRoot(command: String) {
        try { val p = Runtime.getRuntime().exec("su")
            val os = java.io.DataOutputStream(p.outputStream)
            os.writeBytes("$command\n"); os.writeBytes("exit\n"); os.flush(); os.close(); p.waitFor()
        } catch (_: Exception) {}
    }
}
