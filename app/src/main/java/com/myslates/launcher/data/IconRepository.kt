package com.myslates.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.myslates.launcher.R
import com.myslates.launcher.utils.IconUtils

class IconRepository(
    private val context: Context,
    private val iconCache: IconCache
) {

    suspend fun getIcon(packageName: String, fallbackResId: Int? = null): Drawable {
        IconUtils.getInstalledAppIconOrNull(context, packageName)?.let { installedIcon ->
            cacheInstalledIcon(packageName, installedIcon)
            return installedIcon
        }

        iconCache.loadBitmap(packageName)?.let { bitmap ->
            return BitmapDrawable(context.resources, bitmap)
        }

        return fallbackResId?.let { resId ->
            ContextCompat.getDrawable(context, resId)
        } ?: defaultFallback()
        ?: throw IllegalStateException("No fallback icon found")
    }

    fun getCachedIconOnly(packageName: String): Drawable? {
        val bitmap = iconCache.loadBitmap(packageName) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    fun cacheInstalledIcon(packageName: String, drawable: Drawable) {
        val bitmap = drawableToBitmap(drawable)
        iconCache.saveBitmap(packageName, bitmap)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 192
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 192

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun defaultFallback(): Drawable? {
        return ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
    }
}