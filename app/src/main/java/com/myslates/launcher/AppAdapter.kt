package com.myslates.launcher

import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

class AppAdapter(
    private val context: Context,
    private var apps: List<AppObject>,
    private val onAppDrag: (AppObject) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = apps.size

    override fun getItem(position: Int): AppObject = apps[position]  // 👈 cast to AppObject

    override fun getItemId(position: Int): Long = position.toLong()

    fun updateData(newApps: List<AppObject>) {
        this.apps = newApps
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(context)
        val view = convertView ?: inflater.inflate(R.layout.item_app, parent, false)

        val iconView = view.findViewById<ImageView>(R.id.app_icon)
        val labelView = view.findViewById<TextView>(R.id.app_label)

        val app = getItem(position)

        // Modern app icon styling
        iconView.apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(context, R.drawable.modern_icon_bg)
            clipToOutline = true
            elevation = 8f
        }

        labelView.text = app.label
        labelView.setTextColor(Color.WHITE)

        // Regular click to launch app
        view.setOnClickListener {
            Log.d("AppAdapter", "Clicked on ${app.label}")
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                intent?.let { context.startActivity(it) }
            } catch (e: Exception) {
                Log.e("AppAdapter", "Failed to launch ${app.label}", e)
            }
        }

        // Long click for drag
        view.setOnLongClickListener {
            Log.d("AppAdapter", "Long clicked on ${app.label}")

            val clipData = ClipData.newPlainText("drawer_app", app.packageName)
            val dragView = createDragShadow(app)
            val shadow = View.DragShadowBuilder(dragView)

            view.startDragAndDrop(
                clipData,
                shadow,
                MainActivity.DragData(app, -1, false),
                View.DRAG_FLAG_GLOBAL
            )

            // Trigger drag callback
            onAppDrag(app)
            true
        }

        // Add modern touch feedback
        addTouchFeedback(view)

        return view
    }

    private fun createDragShadow(app: AppObject): View {
        val dragView = LayoutInflater.from(context).inflate(R.layout.drag_shadow_item, null)
        val iconView = dragView.findViewById<ImageView>(R.id.drag_icon)
        val labelView = dragView.findViewById<TextView>(R.id.drag_label)

        iconView.setImageDrawable(app.icon)
        labelView.text = app.label

        // Measure and layout
        dragView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        dragView.layout(0, 0, dragView.measuredWidth, dragView.measuredHeight)

        return dragView
    }

    private fun addTouchFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .alpha(0.7f)
                        .setDuration(100)
                        .start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(100)
                        .start()
                }
            }
            false
        }
    }
}