package com.myslates.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.GridView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // Define allowed apps (package names)
    private val allowedApps = listOf(
        "com.myslates.students",
        "com.myslates.parents",
        "com.myslates.teachers",
        "com.adobe.reader" // PDF reader
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val appGridView: GridView = findViewById(R.id.app_grid)
        val packageManager = packageManager

        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app -> allowedApps.contains(app.packageName) }
            .map { app ->
                val label = packageManager.getApplicationLabel(app).toString()
                val icon = packageManager.getApplicationIcon(app)
                AppObject(label, icon, app.packageName)
            }

        val adapter = AppAdapter(this, apps)
        appGridView.adapter = adapter

        appGridView.setOnItemClickListener { _, _, position, _ ->
            val selectedApp = apps[position]
            val launchIntent = packageManager.getLaunchIntentForPackage(selectedApp.packageName)
            launchIntent?.let { startActivity(it) }
        }
    }
}
