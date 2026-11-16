package com.example.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class DeviceOem(val displayName: String) {
    XIAOMI("شاومي / ريدمي / بوكو (MIUI / HyperOS)"),
    SAMSUNG("سامسونج (OneUI)"),
    HUAWEI("هواوي / هونر (EMUI / MagicOS)"),
    OPPO_REALME("أوبو / ريلمي / ون بلس (ColorOS)"),
    VIVO("فيفو / iQOO (FuntouchOS)"),
    GENERIC("أندرويد القياسي")
}

data class BackgroundProtectionState(
    val isBatteryOptimizationIgnored: Boolean,
    val isNotificationsEnabled: Boolean,
    val isPlaybackActive: Boolean,
    val deviceOem: DeviceOem,
    val manufacturerLabel: String,
    val oemSteps: List<String>
) {
    val isFullyProtected: Boolean
        get() = isBatteryOptimizationIgnored && isNotificationsEnabled
}

object BackgroundRestrictionManager {

    fun detectOem(): DeviceOem {
        val manufacturer = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return when {
            manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("poco") -> DeviceOem.XIAOMI
            manufacturer.contains("samsung") -> DeviceOem.SAMSUNG
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> DeviceOem.HUAWEI
            manufacturer.contains("oppo") ||
                manufacturer.contains("realme") ||
                manufacturer.contains("oneplus") -> DeviceOem.OPPO_REALME
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> DeviceOem.VIVO
            else -> DeviceOem.GENERIC
        }
    }

    fun getProtectionState(context: Context): BackgroundProtectionState {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }

        val postNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val notificationsEnabled = postNotifGranted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        val oem = detectOem()
        val oemSteps = when (oem) {
            DeviceOem.XIAOMI -> listOf(
                "افتح إعدادات التشغيل التلقائي (Autostart) وفعّله للتطبيق.",
                "من موفر البطارية للتطبيق اختر «بلا قيود» (No restrictions).",
                "في قائمة التطبيقات الحديثة اسحب التطبيق للأسفل واضغط علامة القفل (Lock)."
            )
            DeviceOem.SAMSUNG -> listOf(
                "افتح البطارية داخل معلومات التطبيق واختر «غير مقيد» (Unrestricted).",
                "تأكد أن التطبيق ليس ضمن «التطبيقات في وضع السكون» في Device Care."
            )
            DeviceOem.HUAWEI -> listOf(
                "افتح تشغيل التطبيقات (App launch) وألغِ الإدارة التلقائية للتطبيق.",
                "فعّل التشغيل التلقائي والتشغيل في الخلفية يدوياً."
            )
            DeviceOem.OPPO_REALME -> listOf(
                "اسمح بالنشاط في الخلفية (Allow background activity) من إعدادات البطارية.",
                "فعّل التشغيل التلقائي (Auto-launch) واقفل التطبيق في تعدد المهام."
            )
            DeviceOem.VIVO -> listOf(
                "من استخدام البطارية اختر «السماح باستهلاك الطاقة العالي في الخلفية».",
                "أضف التطبيق لقائمة البدء التلقائي (Autostart)."
            )
            DeviceOem.GENERIC -> listOf(
                "اضبط استخدام البطارية للتطبيق على «غير مقيد» (Unrestricted).",
                "أبقِ الإشعارات مفعلة لاستمرار خدمة التشغيل والتحميل بالخلفية."
            )
        }

        return BackgroundProtectionState(
            isBatteryOptimizationIgnored = batteryIgnored,
            isNotificationsEnabled = notificationsEnabled,
            isPlaybackActive = PlaybackManager.hasActivePlayback(),
            deviceOem = oem,
            manufacturerLabel = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            oemSteps = oemSteps
        )
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(context, directIntent)) {
                return true
            }
            val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(context, listIntent)) {
                return true
            }
        }
        return openAppDetailsSettings(context)
    }

    fun openOemBackgroundSettings(context: Context, oem: DeviceOem): Boolean {
        val pkg = context.packageName
        val appLabel = runCatching {
            context.applicationInfo.loadLabel(context.packageManager).toString()
        }.getOrDefault("مشغل الوسائط")

        val candidates: List<Intent> = when (oem) {
            DeviceOem.XIAOMI -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                ).apply {
                    putExtra("package_name", pkg)
                    putExtra("package_label", appLabel)
                }
            )
            DeviceOem.SAMSUNG -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.sm_cn",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                )
            )
            DeviceOem.HUAWEI -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                )
            )
            DeviceOem.OPPO_REALME -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.oplus.battery",
                        "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"
                    )
                )
            )
            DeviceOem.VIVO -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                )
            )
            DeviceOem.GENERIC -> emptyList()
        }

        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (tryStartActivity(context, intent)) {
                return true
            }
        }
        return openAppDetailsSettings(context)
    }

    fun openAppDetailsSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStartActivity(context, intent)
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolved != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
