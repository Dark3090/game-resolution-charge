package com.game.reschange

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.slider.Slider
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter
    private lateinit var toggleModified: SwitchMaterial
    private lateinit var allApps: List<AppInfo>
    private var showOnlyModified = false
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.myToolbar))

        recyclerView   = findViewById(R.id.appList)
        toggleModified = findViewById(R.id.toggleModified)
        recyclerView.layoutManager = LinearLayoutManager(this)

        allApps = getUserInstalledApps()
        adapter = AppListAdapter(allApps) { appInfo ->
            showResolutionDialog(appInfo.packageName)
        }
        recyclerView.adapter = adapter

        toggleModified.setOnCheckedChangeListener { _, isChecked ->
            showOnlyModified = isChecked
            filterAppList()
        }

        findViewById<Button>(R.id.resetButton).setOnClickListener {
            val packages = ResChangePrefs.getAllPackages(this)
            for (pkg in packages) {
                runAsRoot(buildDisableCommand(pkg))
                runAsRoot("am force-stop $pkg")
            }
            ResChangePrefs.clearAll(this)
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "All resolutions reset to default", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText.orEmpty()
                filterAppList()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun filterAppList() {
        var list = allApps
        if (showOnlyModified)
            list = list.filter { ResChangePrefs.getScale(this, it.packageName) < 1.0f }
        if (currentQuery.isNotEmpty()) {
            val q = currentQuery.lowercase(Locale.getDefault())
            list = list.filter {
                it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        adapter.submitList(list)
    }

    // Lista TODOS os apps instalados — nao so os com icone de launcher
    private fun getUserInstalledApps(): List<AppInfo> {
        val pm = packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                app.packageName != packageName
            }
            .distinctBy { it.packageName }
            .map { app ->
                AppInfo(
                    name = pm.getApplicationLabel(app).toString(),
                    packageName = app.packageName,
                    icon = try { pm.getApplicationIcon(app) }
                           catch (_: Exception) { pm.defaultActivityIcon }
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun showResolutionDialog(packageName: String) {
        val savedScale = ResChangePrefs.getScale(this, packageName)
        val isAlt      = ModePrefs.isAlternative(this)
        val modeLabel  = if (isAlt) "Alternative" else "Default"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val modeText = TextView(this).apply {
            textSize = 12f
            setPadding(0, 0, 0, 8)
            text = "Mode: $modeLabel"
            setTextColor(0xFF888888.toInt())
        }

        val scaleText = TextView(this).apply {
            textSize = 18f
            setPadding(0, 0, 0, 20)
            text = "Scale: ${(savedScale * 100).toInt()}%"
        }

        val slider = Slider(this).apply {
            valueFrom    = 0.3f
            valueTo      = 1.0f
            stepSize     = 0.05f
            value        = savedScale
            isTickVisible = true
        }

        slider.addOnChangeListener { _, value, _ ->
            scaleText.text = "Scale: ${(value * 100).toInt()}%"
        }

        layout.addView(modeText)
        layout.addView(scaleText)
        layout.addView(slider)

        MaterialAlertDialogBuilder(this, R.style.MyRoundedDialog)
            .setTitle("Set Resolution Scale")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                var scale = String.format(Locale.US, "%.2f", slider.value).toFloat()

                if (scale == 0.95f) {
                    scale = 0.9f
                    Toast.makeText(this, "95% not supported. Using 90%.", Toast.LENGTH_SHORT).show()
                }

                val appName = allApps.find { it.packageName == packageName }?.name ?: packageName

                if (scale >= 1.0f) {
                    runAsRoot(buildDisableCommand(packageName))
                    ResChangePrefs.removeScale(this, packageName)
                    Toast.makeText(this, "Resolution reset to 100% for $appName", Toast.LENGTH_SHORT).show()
                } else {
                    runAsRoot(buildApplyCommand(packageName, scale))
                    ResChangePrefs.saveScale(this, packageName, scale)
                    Toast.makeText(this,
                        "${(scale * 100).toInt()}% applied for $appName [$modeLabel Mode]",
                        Toast.LENGTH_SHORT).show()
                }

                adapter.notifyDataSetChanged()
                runAsRoot("am force-stop $packageName")
                Toast.makeText(this, "$appName stopped. Relaunch to apply.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset") { _, _ ->
                runAsRoot(buildDisableCommand(packageName))
                ResChangePrefs.removeScale(this, packageName)
                adapter.notifyDataSetChanged()
                runAsRoot("am force-stop $packageName")
                val appName = allApps.find { it.packageName == packageName }?.name ?: packageName
                Toast.makeText(this, "$appName reset to 100%. Relaunch to apply.", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    // Monta o comando de aplicar conforme o modo ativo
    private fun buildApplyCommand(pkg: String, scale: Float): String {
        val scaleStr = String.format(Locale.US, "%.2f", scale)
        return if (ModePrefs.isAlternative(this)) {
            "device_config put game_overlay $pkg " +
                "mode=2,downscaleFactor=$scaleStr:mode=3,downscaleFactor=$scaleStr"
        } else {
            "cmd game downscale $scaleStr $pkg 2>/dev/null; " +
                "cmd game set --downscale $scaleStr $pkg 2>/dev/null"
        }
    }

    // Monta o comando de desabilitar conforme o modo ativo
    private fun buildDisableCommand(pkg: String): String {
        return if (ModePrefs.isAlternative(this)) {
            "device_config delete game_overlay $pkg"
        } else {
            "cmd game downscale disable $pkg 2>/dev/null; " +
                "cmd game set --downscale disable $pkg 2>/dev/null"
        }
    }

    private fun runAsRoot(command: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            process.waitFor()
        } catch (e: Exception) {
            Toast.makeText(this, "Root failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
