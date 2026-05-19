package com.secscan.native

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var list: ListView
    private lateinit var nm: NotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        list = findViewById(R.id.list)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("SEC", "Alertas", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Notificaciones de seguridad"
            nm.createNotificationChannel(channel)
        }

        findViewById<Button>(R.id.btn_perms).setOnClickListener { scanPerms() }
        findViewById<Button>(R.id.btn_active).setOnClickListener { checkActive() }
        findViewById<Button>(R.id.btn_stats).setOnClickListener { showStats() }
    }

    private fun scanPerms() {
        val pm = packageManager
        val dangerous = listOf("CAMERA", "ACCESS_FINE_LOCATION", "READ_CONTACTS", "RECORD_AUDIO", "READ_SMS", "READ_CALL_LOG")
        val results = mutableListOf<String>()
        
        for (app in pm.getInstalledApplications(0)) {
            try {
                val info = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                info.requestedPermissions?.forEach { perm ->
                    dangerous.forEach { d -> if (perm.contains(d)) results.add("${app.loadLabel(pm)} → $d") }
                }
            } catch (_: Exception) {}
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, results)
        sendAlert("Escaneo completado", "${results.size} apps con permisos sensibles")
    }

    private fun checkActive() {
        val hasUsage = try {
            val ops = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), packageName) == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }

        if (!hasUsage) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("⚠️ Activa 'Acceso a datos de uso' en Ajustes"))
            return
        }

        val usm = getSystemService("usagestats") as UsageStatsManager
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, -5)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
        
        val active = mutableListOf<String>()
        var crashed = 0
        for (s in stats) {
            if (s.totalTimeInForeground > 0) active.add("${s.packageName} (${s.totalTimeInForeground / 1000}s activo)")
            else if (s.lastTimeUsed > cal.timeInMillis) crashed++
        }
        if (crashed > 0) active.add("⚠️ ~$crashed apps cerradas/inactivas recientemente")
        if (active.isEmpty()) active.add("ℹ️ Sin actividad registrada en los últimos 5 min")
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, active)
        sendAlert("Monitor activo", "${active.size} procesos verificados")
    }

    private fun showStats() {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val bat = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val sf = StatFs("/storage/emulated/0")
        val freeGB = sf.freeBytes / 1073741824
        val totalGB = sf.totalBytes / 1073741824
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val ramMB = mi.availMem / 1048576

        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf(
            "🔋 Batería: $bat%",
            "💾 Almacenamiento: $freeGB GB libres / $totalGB GB total",
            "🧠 RAM disponible: $ramMB MB"
        ))
    }

    private fun sendAlert(title: String, msg: String) {
        val n = NotificationCompat.Builder(this, "SEC")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        nm.notify(System.currentTimeMillis().toInt(), n)
    }
}
