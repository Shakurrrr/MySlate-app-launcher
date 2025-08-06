package com.myslates.launcher

import android.graphics.drawable.Drawable

data class DroppedApp(
    val label: String,
    val icon: Drawable,
    val packageName: String,
    val x: Float,
    val y: Float
)