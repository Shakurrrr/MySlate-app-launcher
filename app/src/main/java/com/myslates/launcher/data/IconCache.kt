package com.myslates.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

class IconCache(private val context: Context) {

    private val iconDir = File(context.filesDir, "icons").apply { mkdirs() }

    fun getIconFile(packageName: String): File {
        return File(iconDir, "$packageName.webp")
    }

    fun exists(packageName: String): Boolean {
        return getIconFile(packageName).exists()
    }

    fun loadBitmap(packageName: String): Bitmap? {
        val file = getIconFile(packageName)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun saveBitmap(packageName: String, bitmap: Bitmap) {
        val file = getIconFile(packageName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
        }
    }
}