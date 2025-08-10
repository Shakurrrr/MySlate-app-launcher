package com.myslates.launcher

import android.view.View

data class DragData(
    val app: AppObject,
    val originalPosition: Int = -1,
    val isFromHomeScreen: Boolean,
    val source: Source = Source.UNKNOWN,
    val sourceView: View? = null
) {
    enum class Source { HOME, DRAWER, DOCK, UNKNOWN }
}

