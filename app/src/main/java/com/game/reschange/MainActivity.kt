package com.game.reschange

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.io.DataOutputStream
import java.util.Locale

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
            showResolutionBottomSheet(appInfo.packageName)
        }
        recyclerView.adapter = adapter

        // Busca inline via TextInputEditText
        findViewById<TextInputEditText>(R.id.searchInput)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {
                    currentQuery = s?.toString().orEmpty()
                    filterAppList()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

        // Chips de filtro
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroup)
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            showOnlyModified = false
            filterAppList()
        }
        // Chips individuais para controlar filtro
        findViewById<Chip>(R.id.chipModified).setOnCheckedChangeListener { _, checked ->
            if (checked) { showOnlyModified = true; filterAppList() }
        }
        findViewById<Chip>(R.id.chipAll).setOnCheckedChangeListener { _, checked ->
            if (checked) { showOnlyModified = false; filterAppList() }
        }

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

        updateStatusBanner()
    }

    override fun onResume() {
        super.onResume()
        updateStatusBanner()
    }

    // Banner dinâmico: verifica se o módulo Xposed está ativo de verdade
    private fun updateStatusBanner() {
        val banner  = findViewById<MaterialCardView>(R.id.statusBanner)
        val label   = findViewById<TextView>(R.id.statusLabel)
        val sub     = findViewById<TextView>(R.id.statusSub)
        val modeStr = if (ModePrefs.isAlternative(this)) "Modo Alternativo" else "Modo Padrão"

        val hasRoot = isRootAvailable()

        if (hasRoot) {
            banner.setCardBackgroundColor(Color.parseColor("#FF003730"))
            banner.strokeColor = Color.parseColor("#FF00B5A0")
            label.text = "Root Disponível"
            label.setTextColor(Color.parseColor("#FF6FF7E8"))
        } else {
            banner.setCardBackgroundColor(Color.parseColor("#FF3B0008"))
            banner.strokeColor = Color.parseColor("#FFFF5449")
            label.text = "Sem Root"
            label.setTextColor(Color.parseColor("#FFFFB4AC"))
        }
        sub.text = if (hasRoot) "Root ativo · $modeStr" else "Root não encontrado"
    }

    private fun isRootAvailable(): Boolean {
        // Método 1: tenta executar "id" via su e verifica se retorna uid=0
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val result = process.inputStream.bufferedReader().readLine() ?: ""
            process.waitFor()
            result.contains("uid=0")
        } catch (_: Exception) {
            // Método 2: verifica se o binário su existe nos caminhos comuns
            listOf("/sbin/su", "/system/bin/su", "/system/xbin/su",
                   "/data/local/xbin/su", "/data/local/bin/su",
                   "/system/sd/xbin/su", "/su/bin/su")
                .any { java.io.File(it).exists() }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Remove item de busca do menu — busca agora é inline
        menu.removeItem(R.id.action_search)
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

    // ── Bottom Sheet de resolução com flags de performance ──────────
    private fun showResolutionBottomSheet(packageName: String) {
        val savedScale = ResChangePrefs.getScale(this, packageName)
        val isAlt      = ModePrefs.isAlternative(this)
        val modeLabel  = if (isAlt) "Alternative Mode" else "Default Mode"
        val appName    = allApps.find { it.packageName == packageName }?.name ?: packageName
        val savedFlags = ResChangePrefs.getFlags(this, packageName)

        val sheet = BottomSheetDialog(this, R.style.MyRoundedDialog)
        val view  = layoutInflater.inflate(R.layout.sheet_resolution, null)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.sheetAppName).text = appName
        view.findViewById<TextView>(R.id.sheetAppPkg).text  = packageName
        view.findViewById<TextView>(R.id.sheetModeLabel).text = modeLabel

        val scaleLabel = view.findViewById<TextView>(R.id.sheetScaleValue)
        val slider     = view.findViewById<Slider>(R.id.sheetSlider)

        slider.valueFrom     = 0.30f
        slider.valueTo       = 1.00f
        slider.stepSize      = 0.05f
        slider.value         = savedScale
        slider.isTickVisible = true
        scaleLabel.text      = "${(savedScale * 100).toInt()}%"

        slider.addOnChangeListener { _, value, _ ->
            scaleLabel.text = "${(value * 100).toInt()}%"
            scaleLabel.setTextColor(
                if (value >= 1.0f) Color.parseColor("#FF939393")
                else Color.parseColor("#FF00E5CC")
            )
        }

        // Preset chips de resolução
        val presetGroup = view.findViewById<ChipGroup>(R.id.sheetPresetChips)
        listOf(50, 60, 70, 75, 80, 90).forEach { pct ->
            presetGroup.addView(Chip(this).apply {
                text = "$pct%"; isCheckable = true
                isChecked = (savedScale * 100).toInt() == pct
                setTextColor(Color.parseColor("#FF939393"))
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
                chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF303030"))
                chipStrokeWidth = 4f
                setOnClickListener { slider.value = pct / 100f }
            })
        }

        // ── FPS chips ──
        val chipsFps = view.findViewById<ChipGroup>(R.id.chipsFps)
        val fpsList  = listOf("Padrão", "60", "90", "120", "144")
        fpsList.forEach { fps ->
            chipsFps.addView(Chip(this).apply {
                text = if (fps == "Padrão") "Padrão" else "${fps} FPS"
                isCheckable = true
                isChecked = savedFlags.fps == fps
                setTextColor(Color.parseColor("#FF939393"))
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
                chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF303030"))
                chipStrokeWidth = 4f
            })
        }

        // ── Performance mode chips ──
        val chipsPerfMode = view.findViewById<ChipGroup>(R.id.chipsPerfMode)
        val perfModes = listOf("Padrão" to "0", "Performance" to "2", "Bateria" to "1")
        perfModes.forEach { (label, value) ->
            chipsPerfMode.addView(Chip(this).apply {
                text = label; isCheckable = true
                isChecked = savedFlags.perfMode == value
                setTextColor(Color.parseColor("#FF939393"))
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
                chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF303030"))
                chipStrokeWidth = 4f
            })
        }

        // ── Extras switches ──
        val switchBoost = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchLoadingBoost)
        val switchAngle = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAngle)
        switchBoost.isChecked = savedFlags.loadingBoost
        switchAngle.isChecked = savedFlags.angle

        // ── Apply ──
        view.findViewById<View>(R.id.btnApply).setOnClickListener {
            var scale = String.format(Locale.US, "%.2f", slider.value).toFloat()
            if (scale == 0.95f) { scale = 0.9f
                Toast.makeText(this, "95% não suportado. Usando 90%.", Toast.LENGTH_SHORT).show() }

            // Lê seleções de FPS e modo
            val selFpsIdx  = chipsFps.checkedChipId
            val selFps     = if (selFpsIdx == -1) "Padrão"
                             else view.findViewById<Chip>(selFpsIdx).text.toString()
                                 .replace(" FPS", "")
            val selModeIdx = chipsPerfMode.checkedChipId
            val selPerf    = if (selModeIdx == -1) "0"
                             else perfModes[chipsPerfMode.indexOfChild(
                                 view.findViewById(selModeIdx))].second

            val flags = PerformanceFlags(
                fps = selFps,
                perfMode = selPerf,
                loadingBoost = switchBoost.isChecked,
                angle = switchAngle.isChecked
            )

            // Aplica resolução
            if (scale >= 1.0f) {
                runAsRoot(buildDisableCommand(packageName))
                ResChangePrefs.removeScale(this, packageName)
            } else {
                runAsRoot(buildApplyCommand(packageName, scale, flags))
                ResChangePrefs.saveScale(this, packageName, scale)
            }
            ResChangePrefs.saveFlags(this, packageName, flags)

            // Aplica modo de performance via cmd game
            if (selPerf != "0") {
                runAsRoot("cmd game set --mode $selPerf $packageName 2>/dev/null")
            }

            adapter.notifyDataSetChanged()
            runAsRoot("am force-stop $packageName")
            Toast.makeText(this, "$appName configurado. Reabra para aplicar.", Toast.LENGTH_LONG).show()
            sheet.dismiss()
        }

        view.findViewById<View>(R.id.btnReset).setOnClickListener {
            runAsRoot(buildDisableCommand(packageName))
            runAsRoot("cmd game set --mode 0 $packageName 2>/dev/null")
            ResChangePrefs.removeScale(this, packageName)
            ResChangePrefs.removeFlags(this, packageName)
            adapter.notifyDataSetChanged()
            runAsRoot("am force-stop $packageName")
            Toast.makeText(this, "$appName resetado.", Toast.LENGTH_LONG).show()
            sheet.dismiss()
        }

        view.findViewById<View>(R.id.btnCancel).setOnClickListener { sheet.dismiss() }

        sheet.show()
    }

    private fun buildApplyCommand(pkg: String, scale: Float, flags: PerformanceFlags = PerformanceFlags()): String {
        val scaleStr = String.format(Locale.US, "%.2f", scale)
        val sb = StringBuilder()
        if (ModePrefs.isAlternative(this)) {
            var overlay = "mode=2,downscaleFactor=$scaleStr:mode=3,downscaleFactor=$scaleStr"
            if (flags.fps != "Padrão") overlay += ":mode=2,fps=${flags.fps}:mode=3,fps=${flags.fps}"
            if (flags.loadingBoost) overlay += ":mode=2,loadingBoost=1:mode=3,loadingBoost=1"
            if (flags.angle) overlay += ":mode=2,angle=1:mode=3,angle=1"
            sb.append("device_config put game_overlay $pkg \"$overlay\"")
        } else {
            sb.append("cmd game downscale $scaleStr $pkg 2>/dev/null; ")
            sb.append("cmd game set --downscale $scaleStr $pkg 2>/dev/null")
            if (flags.fps != "Padrão") sb.append("; cmd game set --fps ${flags.fps} $pkg 2>/dev/null")
            if (flags.loadingBoost) sb.append("; cmd game set --loading-boost 1 $pkg 2>/dev/null")
        }
        return sb.toString()
    }

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
