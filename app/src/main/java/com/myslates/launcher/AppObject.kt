// AppObject.kt
package com.myslates.launcher

import android.graphics.drawable.Drawable

data class AppObject(
    val label: String,
    val icon: Drawable,
    val packageName: String
)
