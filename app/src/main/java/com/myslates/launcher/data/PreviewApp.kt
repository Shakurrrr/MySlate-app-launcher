package com.myslates.launcher.data

data class PreviewApp(
    val packageName: String,
    val label: String,
    val localFallbackResId: Int? = null
)