package com.myslates.launcher.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

object IconUtils {

    fun getInstalledAppIconOrNull(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }
}