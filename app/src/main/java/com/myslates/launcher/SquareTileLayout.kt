package com.myslates.launcher

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class SquareTileLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Force height equal to width
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
