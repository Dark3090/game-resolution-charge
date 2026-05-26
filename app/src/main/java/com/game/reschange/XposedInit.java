package com.game.reschange;

import android.content.pm.ApplicationInfo;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedInit implements IXposedHookLoadPackage {

    // Arquivo gravado pelo app com chmod 644 — world-readable
    // Formato: "com.exemplo.app=0.80" (uma linha por app)
    private static final String CONFIG_FILE = "/data/local/tmp/reschange_config.txt";

    // Arquivo que indica qual modo esta ativo: "default" ou "alternative"
    private static final String MODE_FILE   = "/data/local/tmp/reschange_mode.txt";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        // ── ESTRATEGIA 1: Hook no system_server ──────────────────────────────
        // O cmd game downscale e device_config game_overlay so funcionam em
        // apps com CATEGORY_GAME. O system_server e quem decide isso.
        // Interceptamos getApplicationInfo* no PackageManagerService e
        // marcamos como CATEGORY_GAME APENAS os apps configurados.
        // Corrige o bug original que marcava TODO app como jogo.
        if ("android".equals(lpparam.packageName)) {

            XposedBridge.hookAllMethods(
                    XposedHelpers.findClass(
                            "com.android.server.pm.PackageManagerService",
                            lpparam.classLoader),
                    "getApplicationInfo",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            ApplicationInfo info = (ApplicationInfo) param.getResult();
                            if (info == null) return;
                            if (isConfigured(info.packageName)) {
                                info.category = ApplicationInfo.CATEGORY_GAME;
                                param.setResult(info);
                            }
                        }
                    });

            // Fallback para ROMs que usam getApplicationInfoAsUser
            XposedBridge.hookAllMethods(
                    XposedHelpers.findClass(
                            "com.android.server.pm.PackageManagerService",
                            lpparam.classLoader),
                    "getApplicationInfoAsUser",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            ApplicationInfo info = (ApplicationInfo) param.getResult();
                            if (info == null) return;
                            if (isConfigured(info.packageName)) {
                                info.category = ApplicationInfo.CATEGORY_GAME;
                                param.setResult(info);
                            }
                        }
                    });

            return;
        }

        // ── ESTRATEGIA 2: Hook no processo do app alvo ────────────────────────
        // Reaplicar o comando correto toda vez que o app abre.
        // Resolve o bug de "precisa aplicar toda vez".
        // Roda em beforeHookedMethod (sincrono) para garantir que o
        // comando e executado antes do primeiro frame ser renderizado.
        final String pkg   = lpparam.packageName;
        final float  scale = readScale(pkg);

        if (scale > 0f && scale < 1.0f) {
            XposedBridge.hookAllMethods(
                    XposedHelpers.findClass("android.app.Application", lpparam.classLoader),
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            applyScale(pkg, scale);
                        }
                    });
        }
    }

    private boolean isConfigured(String pkg) {
        return readScale(pkg) < 1.0f;
    }

    private float readScale(String pkg) {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) return 1.0f;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith(pkg + "=")) {
                    return Float.parseFloat(line.split("=", 2)[1].trim());
                }
            }
        } catch (Exception ignored) {}
        return 1.0f;
    }

    private String readMode() {
        File file = new File(MODE_FILE);
        if (!file.exists()) return "default";
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            if (line != null) return line.trim();
        } catch (Exception ignored) {}
        return "default";
    }

    /**
     * Aplica a escala usando o modo correto:
     * - default:     cmd game downscale <scale> <pkg>
     * - alternative: device_config put game_overlay <pkg> mode=2,...
     *
     * O Alternative Mode usa device_config que e mais persistente,
     * porem pode nao funcionar em todas as ROMs.
     */
    private void applyScale(String pkg, float scale) {
        String mode = readMode();
        String cmd;

        String scaleStr = String.format(java.util.Locale.US, "%.2f", scale);

        if ("alternative".equals(mode)) {
            // Alternative Mode: device_config game_overlay
            // Mais persistente — sobrevive ao reboot via namespace sync
            cmd = "device_config put game_overlay " + pkg +
                    " mode=2,downscaleFactor=" + scaleStr +
                    ":mode=3,downscaleFactor=" + scaleStr;
        } else {
            // Default Mode: cmd game downscale
            // Mais estavel e compativel com Android 14+
            cmd = "cmd game downscale " + scaleStr + " " + pkg +
                    " 2>/dev/null; cmd game set --downscale " + scaleStr + " " + pkg + " 2>/dev/null";
        }

        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            p.waitFor();
        } catch (Exception ignored) {}
    }
}
