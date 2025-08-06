package com.myslates.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class DraggedAppView(context: Context, icon: Drawable, label: String) : LinearLayout(context) {

    init {
        LayoutInflater.from(context).inflate(R.layout.dragged_app_view, this, true)

        val iconView = findViewById<ImageView>(R.id.dragged_app_icon)
        val labelView = TextView(context)

        iconView.setImageDrawable(icon)
        labelView.text = label
        labelView.textSize = 12f
        labelView.setTextColor(context.getColor(android.R.color.white))

        orientation = VERTICAL
        addView(labelView)

        // Make it semi-transparent during drag
        alpha = 0.8f
        scaleX = 0.9f
        scaleY = 0.9f
    }
}