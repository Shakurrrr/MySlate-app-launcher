package com.myslates.launcher

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.GridView
import androidx.appcompat.app.AppCompatActivity
import com.myslates.launcher.AppAdapter
import com.myslates.launcher.AppObject

class MainActivity : AppCompatActivity() {

    // Define allowed apps (whitelisted package names)
    private val allowedApps = listOf(
        "com.ATS.MySlates.Parent",
        "com.ATS.MySlates",
        "com.ATS.MySlates.Teacher",
        "com.adobe.reader"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val appGridView: GridView = findViewById(R.id.app_grid)
        val packageManager = packageManager

        val allApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        // Filter and map whitelisted apps only
        val apps = allApps
            .filter { app -> allowedApps.contains(app.packageName) }
            .map { app ->
                val label = packageManager.getApplicationLabel(app).toString()
                val icon = packageManager.getApplicationIcon(app)
                AppObject(label, icon, app.packageName)
            }
        // Log result AFTER declaring `apps`

        Log.d("LauncherDebug", "Filtered apps count: ${apps.size}")
        apps.forEach {
            Log.d("LauncherDebug", "Whitelisted: ${it.packageName}")
        }
        // Set adapter with filtered apps
        val adapter = AppAdapter(this@MainActivity, apps)
        appGridView.adapter = adapter
    }
}
