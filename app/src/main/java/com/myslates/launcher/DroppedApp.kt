package com.myslates.launcher

import android.graphics.drawable.Drawable
import android.view.View

data class DroppedApp(
    val label: String,
    val icon: Drawable,
    val packageName: String,
    var x: Float,
    var y: Float,
    var view: View? = null
)
